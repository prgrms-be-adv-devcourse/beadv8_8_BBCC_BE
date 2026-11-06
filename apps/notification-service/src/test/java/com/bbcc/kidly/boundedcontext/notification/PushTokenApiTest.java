package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.shared.auth.MemberHeaders;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 웹 푸시 FID 등록 · 해제 API. 로그인 회원은 Gateway가 붙이는 X-Member-Id 헤더로 보낸다.
 */
@FunctionalTest
class PushTokenApiTest {

    private static final long ME = 1L;
    private static final long OTHER = 2L;
    private static final String URI = "/api/v1/notifications/push-tokens";

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("FID를 등록하면 204이고, 같은 FID를 다시 등록해도 한 건이다")
    void registerIdempotent() {
        put(ME, "fid-1").expectStatus().isNoContent();
        put(ME, "fid-1").expectStatus().isNoContent();

        assertThat(owners()).containsExactly("fid-1:1");
    }

    @Test
    @DisplayName("같은 브라우저에 다른 회원이 로그인해 같은 FID를 등록하면 주인을 바꾼다")
    void changeOwner() {
        put(ME, "fid-1").expectStatus().isNoContent();
        put(OTHER, "fid-1").expectStatus().isNoContent();

        assertThat(owners()).containsExactly("fid-1:2");
    }

    @Test
    @DisplayName("회원 한 명에 10개를 넘으면 마지막 등록이 가장 오래된 FID부터 지운다")
    void keepNewestTen() {
        IntStream.rangeClosed(1, 11).forEach(i -> put(ME, "fid-" + i).expectStatus().isNoContent());
        put(ME, "fid-2").expectStatus().isNoContent();
        put(ME, "fid-12").expectStatus().isNoContent();

        assertThat(fidsOf(ME)).hasSize(10).doesNotContain("fid-1", "fid-3").contains("fid-2", "fid-12");
    }

    @Test
    @DisplayName("로그아웃 때 내 FID를 지운다. 없거나 남의 FID여도 204이고 남의 것은 그대로 둔다")
    void unregister() {
        put(ME, "fid-1").expectStatus().isNoContent();
        put(OTHER, "fid-2").expectStatus().isNoContent();

        delete(ME, "fid-1").expectStatus().isNoContent();
        delete(ME, "fid-2").expectStatus().isNoContent();
        delete(ME, "fid-none").expectStatus().isNoContent();

        assertThat(owners()).containsExactly("fid-2:2");
    }

    @Test
    @DisplayName("FID가 비었거나 너무 길면 400이다")
    void invalidFid() {
        put(ME, " ").expectStatus().isBadRequest();
        put(ME, "a".repeat(1025)).expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("로그인하지 않으면 401이다")
    void unauthenticated() {
        client.put().uri(URI).contentType(MediaType.APPLICATION_JSON).body("{\"fid\":\"fid-1\"}")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    private RestTestClient.ResponseSpec put(long memberId, String fid) {
        return client.put().uri(URI).headers(member(memberId)).contentType(MediaType.APPLICATION_JSON)
            .body("{\"fid\":\"" + fid + "\"}")
            .exchange();
    }

    private RestTestClient.ResponseSpec delete(long memberId, String fid) {
        return client.method(HttpMethod.DELETE).uri(URI).headers(member(memberId))
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"fid\":\"" + fid + "\"}")
            .exchange();
    }

    private List<String> owners() {
        return jdbcTemplate.queryForList(
            "SELECT fid || ':' || member_id FROM notification.push_token ORDER BY fid", String.class);
    }

    private List<String> fidsOf(long memberId) {
        return jdbcTemplate.queryForList(
            "SELECT fid FROM notification.push_token WHERE member_id = ?", String.class, memberId);
    }

    private static Consumer<HttpHeaders> member(long memberId) {
        return headers -> headers.set(MemberHeaders.MEMBER_ID, String.valueOf(memberId));
    }
}
