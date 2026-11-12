package com.bbcc.kidly.boundedcontext.chatbot;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 챗봇 서비스가 챗봇 도메인만으로 뜨고, 챗봇 스키마만 만드는지 본다.
 */
@FunctionalTest
class ChatbotServiceApplicationApiTest {

    private static final long MEMBER_ID = 1L;

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

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/products/1", "/api/v1/orders", "/api/v1/reviews/summary?productId=1"})
    @DisplayName("다른 도메인 API는 없어 ProblemDetail 형식의 404를 받는다")
    void noOtherDomainApi(String path) {
        client.get().uri(path)
            .headers(TestMembers.buyer(MEMBER_ID))
            .exchange()
            .expectStatus().isNotFound()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(404);
    }

    @Test
    @DisplayName("빈 DB에 챗봇 마이그레이션을 적용하고, 테이블은 챗봇 스키마에만 만든다")
    void migratesOnlyChatbotSchema() {
        var schemas = jdbcTemplate.queryForList("""
            SELECT DISTINCT schemaname FROM pg_tables
            WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
            """, String.class);
        var tables = jdbcTemplate.queryForList("""
            SELECT tablename FROM pg_tables WHERE schemaname = 'chatbot' ORDER BY tablename
            """, String.class);

        assertThat(schemas).containsExactly("chatbot");
        assertThat(tables).containsExactly("flyway_schema_history_chatbot", "knowledge_document");
    }
}
