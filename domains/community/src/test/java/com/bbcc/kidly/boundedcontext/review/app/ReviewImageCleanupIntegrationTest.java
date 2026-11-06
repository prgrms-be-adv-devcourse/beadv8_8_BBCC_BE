package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.bbcc.kidly.boundedcontext.review.app.ReviewImageCleanupService.CleanupRun;
import com.bbcc.kidly.boundedcontext.review.app.ReviewImageCleanupService.PendingRun;
import com.bbcc.kidly.boundedcontext.review.app.ReviewImageCleanupService.SweepRun;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageStorage;
import com.bbcc.kidly.boundedcontext.review.out.storage.FakeReviewImageObjectStore;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.boundedcontext.support.QueryCounter;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 사진 정리 처리(스케줄러가 부르는 run)를 실제 DB와 테스트용 저장소(fake)로 확인한다.
 * <ul>
 *   <li>정리 대기: 차례 전은 그대로, 지우면 행 삭제, 실패는 시도 +1 · 백오프(상한 max-backoff), 지금 붙어 있거나 모양이 틀린 key는 지우지 않고
 *       행만 삭제, 서버 둘이 동시에 돌아도 한 번</li>
 *   <li>훑기: 유예 전 · 붙어 있음 · 대기 중 · 모양 틀림 · 다른 환경은 남기고 나머지만, 한 주기 삭제 상한, 쪽 이어 읽기, 쿼리 수는 파일 수와 무관</li>
 * </ul>
 * 로그에 key · 주소를 남기지 않는다.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ReviewImageCleanupIntegrationTest {

    private static final long ME = 13L;

    @Autowired
    private ReviewImageCleanupService service;

    @Autowired
    private ReviewImageCleanupProperties properties;

    @Autowired
    private ReviewImageStorage imageStorage;

    @Autowired
    private ReviewImageCleanupStorage cleanupStorage;

    @Autowired
    private FakeReviewImageObjectStore store;

    @Autowired
    private QueryCounter queryCounter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeEach
    void reset() {
        store.reset();
        service.restartScan();
    }

    @Test
    @DisplayName("테스트 프로필은 정리 스케줄러를 띄우지 않는다(처리 메서드를 직접 부른다)")
    void schedulerOffInTests() {
        assertThat(properties.enabled()).isFalse();
        assertThat(applicationContext.containsBean("communityReviewImageCleanupScheduler")).isFalse();
    }

    // ---- 정리 대기 ----

    @Test
    @DisplayName("차례(next_attempt_at)가 지난 대기는 저장소에서 지우고 행을 지운다. 차례 전은 그대로 둔다")
    void deletesDuePending() {
        insertPending(key(0), LocalDateTime.now().minusSeconds(1), 0);
        insertPending(key(1), LocalDateTime.now().plusMinutes(30), 0);
        store.put(key(0), Instant.now());

        PendingRun run = service.processPending();

        assertThat(run).isEqualTo(new PendingRun(1, 0, 0, 0));
        assertThat(store.deleteRequests()).containsExactly(key(0));
        assertThat(store.contains(key(0))).isFalse();
        assertThat(pendingKeys()).containsExactly(key(1));
    }

    @Test
    @DisplayName("실패하면 시도 +1 · 다음 시각 = 지금 + 1분 · 2분 …(두 배씩) · 실패 종류를 남기고, 다음 시각은 max-backoff에서 멈춘다")
    void failureBacksOff() {
        insertPending(key(0), LocalDateTime.now().minusSeconds(1), 0);
        insertPending(key(1), LocalDateTime.now().minusSeconds(1), 30);
        store.failDelete(key(0), "S3Exception:403");
        store.failDelete(key(1), "SdkClientException");

        LocalDateTime before = LocalDateTime.now();
        PendingRun run = service.processPending();

        assertThat(run).isEqualTo(new PendingRun(0, 0, 0, 2));
        Map<String, Object> first = pendingRow(key(0));
        assertThat(first).containsEntry("attempts", 1).containsEntry("last_failure_type", "S3Exception:403");
        assertThat(time(first, "next_attempt_at")).isCloseTo(before.plusMinutes(1), within(5, ChronoUnit.SECONDS));
        Map<String, Object> capped = pendingRow(key(1));
        assertThat(capped).containsEntry("attempts", 31).containsEntry("last_failure_type", "SdkClientException");
        assertThat(time(capped, "next_attempt_at")).isCloseTo(before.plus(properties.maxBackoff()),
            within(5, ChronoUnit.SECONDS));

        jdbcTemplate.update("update community.review_image_cleanup set next_attempt_at = ? where object_key = ?",
            LocalDateTime.now().minusSeconds(1), key(0));
        service.processPending();
        assertThat(pendingRow(key(0))).containsEntry("attempts", 2);
        assertThat(time(pendingRow(key(0)), "next_attempt_at")).isCloseTo(LocalDateTime.now().plusMinutes(2),
            within(5, ChronoUnit.SECONDS));

        store.clearFailure(key(0));
        jdbcTemplate.update("update community.review_image_cleanup set next_attempt_at = ? where object_key = ?",
            LocalDateTime.now().minusSeconds(1), key(0));
        assertThat(service.processPending().deleted()).isEqualTo(1);
        assertThat(pendingKeys()).containsExactly(key(1));
    }

    @Test
    @DisplayName("지우기 직전에 그 주소가 지금 리뷰에 붙어 있으면 지우지 않고 대기만 지운다")
    void keepsAttached() {
        long reviewId = insertReview();
        jdbcTemplate.update("insert into community.review_image (review_id, position, image_url, created_at) "
            + "values (?, 0, ?, now())", reviewId, url(0));
        insertPending(key(0), LocalDateTime.now().minusSeconds(1), 0);

        PendingRun run = service.processPending();

        assertThat(run).isEqualTo(new PendingRun(0, 1, 0, 0));
        assertThat(store.deleteRequests()).isEmpty();
        assertThat(pendingKeys()).isEmpty();
    }

    @Test
    @DisplayName("정리할 수 있는 모양이 아닌 key(다른 환경 · 다른 기능 · 서버가 만들지 않은 이름)는 저장소를 부르지 않고 대기만 지운다")
    void neverDeletesForeignKeys() {
        String name = "0b6f3c1e-8a2d-4c5b-9e7f-000000000000.jpg";
        List<String> foreign = List.of("other/reviews/13/" + name, prefix() + "products/13/" + name,
            prefix() + "reviews/13/photo.jpg", prefix() + "reviews/0/" + name);
        foreign.forEach(key -> insertPending(key, LocalDateTime.now().minusSeconds(1), 0));

        PendingRun run = service.processPending();

        assertThat(run).isEqualTo(new PendingRun(0, 0, foreign.size(), 0));
        assertThat(store.deleteRequests()).isEmpty();
        assertThat(pendingKeys()).isEmpty();
    }

    @Test
    @DisplayName("한 주기에는 batch-size까지, 다음 시각 · id 순으로 처리한다")
    void batchOrder() {
        LocalDateTime base = LocalDateTime.now().minusMinutes(10);
        for (int i = 0; i < properties.batchSize() + 2; i++) {
            insertPending(key(i), base.plusSeconds(properties.batchSize() + 2 - i), 0);
        }

        assertThat(service.processPending().deleted()).isEqualTo(properties.batchSize());

        assertThat(pendingKeys()).containsExactlyInAnyOrder(key(0), key(1));
    }

    @Test
    @DisplayName("서버 둘이 동시에 돌아도 한 번만 지운다(다른 쪽은 실행 잠금을 못 잡아 건너뜀)")
    void runsOnce() {
        insertPending(key(0), LocalDateTime.now().minusSeconds(1), 0);
        AtomicReference<CleanupRun> concurrent = new AtomicReference<>();
        store.duringNextDelete(() -> concurrent.set(runOnOtherThread()));

        CleanupRun run = service.run();

        assertThat(run.skipped()).isFalse();
        assertThat(run.pending().deleted()).isEqualTo(1);
        assertThat(concurrent.get().skipped()).isTrue();
        assertThat(store.deleteRequests()).containsExactly(key(0));
        assertThat(pendingKeys()).isEmpty();
    }

    // ---- 훑기 ----

    @Test
    @DisplayName("훑기는 유예가 지났고 어느 리뷰에도 붙지 않았으며 대기 중이 아닌 정리 모양의 파일만 지운다")
    void sweepDeletesOnlyOrphans() {
        Instant old = Instant.now().minus(properties.orphanGrace()).minusSeconds(60);
        Instant fresh = Instant.now().minus(properties.orphanGrace()).plusSeconds(600);
        long reviewId = insertReview();
        jdbcTemplate.update("insert into community.review_image (review_id, position, image_url, created_at) "
            + "values (?, 0, ?, now())", reviewId, url(1));
        insertPending(key(3), LocalDateTime.now().plusHours(1), 0);
        store.put(key(0), old);
        store.put(key(1), old);
        store.put(key(2), fresh);
        store.put(key(3), old);
        store.put(prefix() + "reviews/13/not-generated.jpg", old);
        store.put("other/reviews/13/0b6f3c1e-8a2d-4c5b-9e7f-000000000000.jpg", old);

        SweepRun run = service.sweep();

        assertThat(run).isEqualTo(new SweepRun(5, 1, 0, false, false));
        assertThat(store.deleteRequests()).containsExactly(key(0));
        assertThat(store.listRequests()).containsExactly(prefix() + "reviews/|null|" + properties.scanPageSize());
    }

    @Test
    @DisplayName("한 주기 삭제 상한에 닿으면 멈추고, 다음 주기는 같은 쪽을 다시 읽어 나머지를 지운다")
    void sweepDeleteLimit() {
        Instant old = Instant.now().minus(properties.orphanGrace()).minusSeconds(60);
        // 한 쪽(scan-page-size)보다 많이 두어, 상한에 닿은 쪽이 마지막 쪽이 아니어도 같은 쪽을 다시 읽는지 본다
        int total = properties.scanPageSize() + 3;
        for (int i = 0; i < total; i++) {
            store.put(key(i), old);
        }

        SweepRun first = service.sweep();
        SweepRun second = service.sweep();

        assertThat(first.deleted()).isEqualTo(properties.scanDeleteLimit());
        assertThat(first.limitReached()).isTrue();
        assertThat(second.deleted()).isEqualTo(properties.scanDeleteLimit());
        assertThat(store.contains(key(properties.scanDeleteLimit()))).isFalse();
        assertThat(store.listRequests()).hasSize(2)
            .allSatisfy(request -> assertThat(request).contains("|null|"));
    }

    @Test
    @DisplayName("지우지 못한 파일은 상한에 세지 않고 이번 바퀴에서 건너뛴다: 한 쪽이 늘 실패하는 파일로 차도 다음 쪽으로 넘어간다")
    void sweepSkipsFailuresAndMovesOn() {
        Instant old = Instant.now().minus(properties.orphanGrace()).minusSeconds(60);
        int total = properties.scanPageSize() + 2;
        for (int i = 0; i < total; i++) {
            store.put(key(i), old);
            if (i < properties.scanPageSize()) {
                store.failDelete(key(i), "S3Exception:403");
            }
        }

        SweepRun first = service.sweep();
        SweepRun second = service.sweep();

        assertThat(first).isEqualTo(new SweepRun(properties.scanPageSize(), 0, properties.scanPageSize(), false,
            false));
        assertThat(second).isEqualTo(new SweepRun(2, 2, 0, false, false));
        assertThat(store.listRequests().get(1)).contains("|" + key(properties.scanPageSize() - 1) + "|");
    }

    @Test
    @DisplayName("쪽을 이어 읽고(표지는 메모리), 마지막 쪽 다음에는 처음부터 다시 읽는다")
    void sweepContinues() {
        Instant fresh = Instant.now();
        int total = properties.scanPageSize() + 1;
        for (int i = 0; i < total; i++) {
            store.put(key(i), fresh);
        }

        assertThat(service.sweep().listed()).isEqualTo(properties.scanPageSize());
        assertThat(service.sweep().listed()).isEqualTo(1);
        service.sweep();

        List<String> requests = store.listRequests();
        assertThat(requests).hasSize(3);
        assertThat(requests.get(0)).contains("|null|");
        assertThat(requests.get(1)).contains("|" + key(properties.scanPageSize() - 1) + "|");
        assertThat(requests.get(2)).contains("|null|");
    }

    @Test
    @DisplayName("한 쪽의 붙어 있는지 · 대기 중인지 확인 쿼리 수는 파일 수와 상관없다")
    void sweepQueryCountIndependentOfFiles() {
        Instant old = Instant.now().minus(properties.orphanGrace()).minusSeconds(60);
        for (int i = 0; i < 3; i++) {
            store.put(key(i), old);
        }
        long few = queryCounter.count(service::sweep);

        store.reset();
        service.restartScan();
        for (int i = 0; i < 30; i++) {
            store.put(key(100 + i), old);
        }
        long many = queryCounter.count(service::sweep);

        assertThat(few).isEqualTo(many).isPositive();
    }

    @Test
    @DisplayName("목록을 읽지 못하면 아무것도 지우지 않고 다음 주기에 같은 자리부터 다시 읽는다")
    void sweepListFailure() {
        store.put(key(0), Instant.now().minus(Duration.ofDays(30)));
        store.listUnavailable(true);

        assertThat(service.sweep()).isEqualTo(new SweepRun(0, 0, 0, false, true));

        store.listUnavailable(false);
        assertThat(service.sweep().deleted()).isEqualTo(1);
    }

    @Test
    @DisplayName("한 주기 로그에는 건수 · 결과만 있고 key · 주소가 없다")
    void logsWithoutKeys(CapturedOutput output) {
        insertPending(key(0), LocalDateTime.now().minusSeconds(1), 0);
        insertPending(key(1), LocalDateTime.now().minusSeconds(1), 0);
        store.failDelete(key(1), "S3Exception:500");
        store.put(key(2), Instant.now().minus(Duration.ofDays(30)));

        service.run();

        assertThat(output.getAll()).contains("리뷰 사진 정리").doesNotContain("0b6f3c1e").doesNotContain("/reviews/");
    }

    private CleanupRun runOnOtherThread() {
        AtomicReference<CleanupRun> result = new AtomicReference<>();
        Thread thread = new Thread(() -> result.set(service.run()));
        thread.start();
        try {
            thread.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result.get();
    }

    private long insertReview() {
        jdbcTemplate.update("insert into community.review (order_item_id, product_id, member_id, nickname, rating, "
            + "content, created_at, updated_at) values (11, 14, ?, '닉', 4, '본문', now(), now())", ME);
        return jdbcTemplate.queryForObject("select id from community.review where order_item_id = 11", Long.class);
    }

    private void insertPending(String key, LocalDateTime nextAttemptAt, int attempts) {
        jdbcTemplate.update("insert into community.review_image_cleanup (object_key, reason, attempts, "
            + "next_attempt_at, created_at) values (?, 'REPLACED', ?, ?, ?)", key, attempts, nextAttemptAt,
            LocalDateTime.now().minusHours(2));
    }

    private Map<String, Object> pendingRow(String key) {
        return jdbcTemplate.queryForMap("select attempts, next_attempt_at, last_failure_type "
            + "from community.review_image_cleanup where object_key = ?", key);
    }

    private static LocalDateTime time(Map<String, Object> row, String column) {
        return ((Timestamp) row.get(column)).toLocalDateTime();
    }

    private List<String> pendingKeys() {
        return jdbcTemplate.queryForList("select object_key from community.review_image_cleanup", String.class);
    }

    private String url(int index) {
        return imageStorage.ownImageUrlPrefix(ME) + String.format("0b6f3c1e-8a2d-4c5b-9e7f-%012d", index) + ".jpg";
    }

    private String key(int index) {
        return cleanupStorage.keyOf(url(index)).orElseThrow();
    }

    // 이 환경 key 맨 앞 경로(예: test/)
    private String prefix() {
        String key = key(0);
        return key.substring(0, key.indexOf("reviews/"));
    }
}
