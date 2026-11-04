package com.bbcc.kidly.boundedcontext.order;

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
 * 주문 서비스가 주문 도메인(주문 · 장바구니 · 배송 · 조르기)만으로 뜨고, 주문 API를 kidly-app과 같은 주소 · 같은 응답 형식으로 내는지 본다.
 * 주문 API의 업무 동작은 주문 모듈(domains/order)의 통합 · 기능 테스트가 본다.
 */
@FunctionalTest
class OrderServiceApplicationApiTest {

    private static final long MEMBER_ID = 1L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // baseline-version은 kidly-app이 마지막으로 적용한 주문 마이그레이션 버전이다. 실제 파일 버전이 아니면
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

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/products/1", "/api/v1/reviews", "/api/v1/favorites", "/api/v1/payments/1"})
    @DisplayName("다른 도메인 API는 없어 kidly-app과 같은 ProblemDetail 형식의 404를 받는다")
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
    @DisplayName("빈 DB에는 init부터 적용하고, baseline-version은 적용한 마이그레이션 버전 중 하나이며, 테이블은 주문 스키마에만 만든다")
    void migratesOnlyOrderSchema() {
        var schemas = jdbcTemplate.queryForList("""
            SELECT DISTINCT schemaname FROM pg_tables
            WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
            """, String.class);
        // version이 없는 행은 Flyway가 스키마를 만든 기록이다
        var versions = jdbcTemplate.queryForList("""
            SELECT version FROM orders.flyway_schema_history_order
            WHERE success AND version IS NOT NULL ORDER BY installed_rank
            """, String.class);

        assertThat(schemas).containsExactly("orders");
        assertThat(versions.getFirst()).isEqualTo("202610160256");
        assertThat(versions).contains(baselineVersion);
    }

    @Test
    @DisplayName("장바구니 조회 API를 kidly-app과 같은 주소로 부르면 같은 형식으로 답한다")
    void cart() {
        client.get().uri("/api/v1/cart")
            .headers(TestMembers.buyer(MEMBER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items").isEmpty()
            .jsonPath("$.totalAmount").isEqualTo(0);
    }

    @Test
    @DisplayName("로그인 헤더가 없으면 주문 API는 kidly-app과 같은 ProblemDetail 형식의 401이다")
    void unauthorizedWithoutLogin() {
        client.get().uri("/api/v1/orders")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(401)
            .jsonPath("$.instance").isEqualTo("/api/v1/orders");
    }
}
