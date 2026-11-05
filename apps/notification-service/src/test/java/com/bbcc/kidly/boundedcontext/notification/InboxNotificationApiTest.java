package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.app.InboxNotificationService;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import com.bbcc.kidly.shared.auth.MemberHeaders;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 알림함 API. 로그인 회원은 Gateway가 붙이는 X-Member-Id 헤더로 보낸다.
 */
@FunctionalTest
class InboxNotificationApiTest {

    private static final long ME = 1L;
    private static final long OTHER = 2L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private InboxNotificationService inboxNotificationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("내 알림만 최신순으로 페이지를 나눠 준다")
    void listNewestFirst() {
        add(ME, "첫째", "/orders/1", "k1");
        add(ME, "둘째", null, "k2");
        add(ME, "셋째", null, "k3");
        add(OTHER, "남의 것", null, "k4");

        client.get().uri("/api/v1/notifications?page=0&size=2").headers(member(ME))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(2)
            .jsonPath("$.content[0].title").isEqualTo("셋째")
            .jsonPath("$.content[0].type").isEqualTo("WELCOME")
            .jsonPath("$.content[0].body").isEqualTo("본문")
            .jsonPath("$.content[0].linkPath").isEmpty()
            .jsonPath("$.content[0].read").isEqualTo(false)
            .jsonPath("$.content[0].notificationId").isNumber()
            .jsonPath("$.content[0].createdAt").isNotEmpty()
            .jsonPath("$.content[1].title").isEqualTo("둘째")
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(2)
            .jsonPath("$.totalElements").isEqualTo(3)
            .jsonPath("$.totalPages").isEqualTo(2)
            .jsonPath("$.hasNext").isEqualTo(true);

        client.get().uri("/api/v1/notifications?page=1&size=2").headers(member(ME))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].title").isEqualTo("첫째")
            .jsonPath("$.content[0].linkPath").isEqualTo("/orders/1")
            .jsonPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("알림이 없으면 빈 목록이다")
    void emptyList() {
        client.get().uri("/api/v1/notifications").headers(member(ME))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isArray()
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(20)
            .jsonPath("$.totalElements").isEqualTo(0);
    }

    @Test
    @DisplayName("같은 중복 키로 두 번 넣으면 한 건만 남는다")
    void addOnceByDedupKey() {
        add(ME, "첫째", null, "same");
        add(ME, "다시", null, "same");

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification.inbox", Long.class))
            .isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 중복 키로 동시에 넣어도 예외 없이 한 건만 남는다")
    void addConcurrentlyOnce() throws Exception {
        int threads = 8;
        var ready = new CountDownLatch(threads);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(threads)) {
            var futures = IntStream.range(0, threads)
                .mapToObj(i -> executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    add(ME, "동시 " + i, null, "same");
                    return null;
                }))
                .toList();
            ready.await();
            start.countDown();
            for (Future<Object> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification.inbox", Long.class))
            .isEqualTo(1L);
    }

    @Test
    @DisplayName("안 읽은 내 알림 개수를 준다. 읽으면 줄고, 두 번 읽어도 204로 같다")
    void unreadCountAndReadTwice() {
        add(ME, "첫째", null, "k1");
        add(ME, "둘째", null, "k2");
        add(OTHER, "남의 것", null, "k3");
        var firstId = idOf("k1");

        expectUnreadCount(2);

        client.patch().uri("/api/v1/notifications/{id}/read", firstId).headers(member(ME))
            .exchange()
            .expectStatus().isNoContent();
        var readAt = jdbcTemplate.queryForObject(
            "SELECT read_at FROM notification.inbox WHERE id = ?", Object.class, firstId);
        client.patch().uri("/api/v1/notifications/{id}/read", firstId).headers(member(ME))
            .exchange()
            .expectStatus().isNoContent();

        expectUnreadCount(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT read_at FROM notification.inbox WHERE id = ?", Object.class, firstId)).isEqualTo(readAt);
        client.get().uri("/api/v1/notifications").headers(member(ME))
            .exchange()
            .expectBody()
            .jsonPath("$.content[1].read").isEqualTo(true);
    }

    @Test
    @DisplayName("남의 알림이나 없는 알림을 읽으면 404 NOTIFICATION_NOT_FOUND이고 바뀌지 않는다")
    void readOthersNotFound() {
        add(OTHER, "남의 것", null, "k1");
        var othersId = idOf("k1");

        client.patch().uri("/api/v1/notifications/{id}/read", othersId).headers(member(ME))
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.status").isEqualTo(404)
            .jsonPath("$.title").isEqualTo("알림을 찾을 수 없습니다");
        client.patch().uri("/api/v1/notifications/{id}/read", othersId + 100).headers(member(ME))
            .exchange()
            .expectStatus().isNotFound();

        assertThat(jdbcTemplate.queryForObject(
            "SELECT read_at FROM notification.inbox WHERE id = ?", Object.class, othersId)).isNull();
    }

    @Test
    @DisplayName("로그인하지 않으면 401이다")
    void requiresLogin() {
        client.get().uri("/api/v1/notifications").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/v1/notifications/unread-count").exchange().expectStatus().isUnauthorized();
        client.patch().uri("/api/v1/notifications/1/read").exchange().expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("size가 1~100을 벗어나면 400이다")
    void invalidSize() {
        client.get().uri("/api/v1/notifications?size=0").headers(member(ME))
            .exchange()
            .expectStatus().isBadRequest();
        client.get().uri("/api/v1/notifications?size=101").headers(member(ME))
            .exchange()
            .expectStatus().isBadRequest();
    }

    private void expectUnreadCount(long count) {
        client.get().uri("/api/v1/notifications/unread-count").headers(member(ME))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.count").isEqualTo(count);
    }

    private void add(long memberId, String title, String linkPath, String dedupKey) {
        inboxNotificationService.add(
            new InboxNotificationInfo(memberId, NotificationType.WELCOME, title, "본문", linkPath, dedupKey));
    }

    private Long idOf(String dedupKey) {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM notification.inbox WHERE dedup_key = ?", Long.class, dedupKey);
    }

    // Gateway가 붙이는 회원 헤더. 서비스는 X-Member-Id만으로 로그인 회원을 만든다
    private static Consumer<HttpHeaders> member(long memberId) {
        return headers -> headers.set(MemberHeaders.MEMBER_ID, String.valueOf(memberId));
    }
}
