package com.bbcc.kidly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.member.support.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 회원 서비스가 회원 · 인증 · 판매자 도메인만으로 뜨고, 빈 DB에 세 스키마만 만드는지 본다.
 */
@FunctionalTest
class MemberServiceApplicationApiTest {

    private static final Long MEMBER_ID = 1L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    @DisplayName("다른 도메인 API는 없다(404)")
    void noOtherDomainApi() {
        client.get().uri("/api/v1/orders")
            .headers(TestMembers.buyer(MEMBER_ID))
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("빈 DB에는 init부터 적용하고, 테이블은 회원 · 인증 · 판매자 스키마에만 만든다")
    void migratesOnlyOwnSchemas() {
        var schemas = jdbcTemplate.queryForList("""
            SELECT DISTINCT schemaname FROM pg_tables
            WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
            """, String.class);
        var baseline = jdbcTemplate.queryForList(
            "SELECT version FROM member.flyway_schema_history_member WHERE type = 'BASELINE'", String.class);

        assertThat(schemas).containsExactlyInAnyOrder("member", "auth", "seller");
        assertThat(baseline).isEmpty();
    }
}
