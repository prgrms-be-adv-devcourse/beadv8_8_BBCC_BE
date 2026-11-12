package com.bbcc.kidly.boundedcontext.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 커뮤니티 서비스가 커뮤니티 도메인(리뷰 · 찜)만으로 뜨고, 커뮤니티 API를 정해진 주소 · 응답 형식으로 내는지 본다.
 * 커뮤니티 API의 업무 동작은 커뮤니티 모듈(domains/community)의 통합 · 기능 테스트가 본다.
 */
@FunctionalTest
class CommunityServiceApplicationApiTest {

    private static final long MEMBER_ID = 1L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // baseline-version은 kidly-app이 마지막으로 적용한 커뮤니티 마이그레이션 버전이다. 실제 파일 버전이 아니면
    // kidly-app이 이미 적용한 DB에서 그 파일까지 건너뛰는 경계가 어긋난다
    @Value("${spring.flyway.baseline-version}")
    private String baselineVersion;

    @Test
    @DisplayName("health가 UP이다")
    void healthUp() {
        client.get().uri("/actuator/health")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    @DisplayName("/actuator/prometheus에 커뮤니티 운영 지표가 나온다(꺼진 스케줄러의 시계열은 없다)")
    void prometheusExposesCommunityMetrics() {
        String body = client.get().uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody();

        assertThat(body).contains(
            "kidly_community_dlt_messages{state=\"open\"}",
            "kidly_community_dlt_oldest_age_seconds",
            "kidly_community_dlt_store_failures_total",
            "kidly_community_dlt_last_stored_seconds",
            "kidly_community_moderation_pending{stage=\"waiting\"}",
            "kidly_community_moderation_pending{stage=\"retrying\"}",
            "kidly_community_moderation_pending{stage=\"exhausted\"}",
            "kidly_community_moderation_pending_oldest_age_seconds",
            "kidly_community_moderation_checks_total{cause=\"none\",result=\"passed\"}",
            "kidly_community_review_image_storage_unavailable_total{operation=\"upload_check\"}",
            "kidly_community_replica_product_last_changed_age_seconds",
            "kidly_community_outbox_messages{state=\"pending\"}",
            "kidly_community_outbox_messages{state=\"quarantined\"}",
            "kidly_community_outbox_oldest_age_seconds",
            "kidly_community_outbox_send_failures_total");
        // 테스트 프로필은 검사 · 사진 정리 스케줄러를 끈다
        assertThat(body).doesNotContain("kidly_community_scheduler_last_success_seconds{");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/products/1", "/api/v1/orders", "/api/v1/cart", "/api/v1/payments/1"})
    @DisplayName("다른 도메인 API는 없어 ProblemDetail 형식의 404를 받는다")
    void noOtherDomainApi(String path) {
        client.get().uri(path)
            .headers(TestMembers.buyer(MEMBER_ID))
            .exchange()
            .expectStatus().isNotFound()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(404)
            .jsonPath("$.title").isNotEmpty()
            .jsonPath("$.instance").isEqualTo(path);
    }

    @Test
    @DisplayName("빈 DB에는 커뮤니티 스키마 마이그레이션부터 적용하고, baseline-version은 적용한 마이그레이션 버전 중 하나이며, 테이블은 커뮤니티 스키마에만 만든다")
    void migratesOnlyCommunitySchema() {
        var schemas = jdbcTemplate.queryForList("""
            SELECT DISTINCT schemaname FROM pg_tables
            WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
            """, String.class);
        // version이 없는 행은 Flyway가 스키마를 만든 기록이다
        var versions = jdbcTemplate.queryForList("""
            SELECT version FROM community.flyway_schema_history_community
            WHERE success AND version IS NOT NULL ORDER BY installed_rank
            """, String.class);

        assertThat(schemas).containsExactly("community");
        assertThat(versions.getFirst()).isEqualTo("202611020103");
        assertThat(versions).contains(baselineVersion);
    }

    @Test
    @DisplayName("리뷰 · 찜 요약 API를 부르면 정해진 형식으로 답한다(로그인 없이, ID는 문자열)")
    void summaries() {
        client.get().uri("/api/v1/reviews/summary?productId=1")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.productId").isEqualTo("1")
            .jsonPath("$.reviewCount").isEqualTo(0);

        client.get().uri("/api/v1/favorites/summary?productId=1")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.productId").isEqualTo("1")
            .jsonPath("$.favoriteCount").isEqualTo(0);
    }

    @Test
    @DisplayName("로그인 헤더가 없으면 내 찜 API는 ProblemDetail 형식의 401이다")
    void unauthorizedWithoutLogin() {
        client.get().uri("/api/v1/favorites")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(401)
            .jsonPath("$.instance").isEqualTo("/api/v1/favorites");
    }
}
