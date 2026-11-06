package com.bbcc.kidly.boundedcontext.search;

import com.bbcc.kidly.shared.auth.MemberHeaders;
import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 검색 결과 클릭 기록 API(POST /api/v1/search/clicks)를 HTTP 요청부터 응답까지 본다(#1004).
 * 이벤트가 Kafka로 나가는지는 SearchPerformedKafkaIntegrationTest가 본다.
 */
@FunctionalTest
class SearchClickApiTest {

    private static final String PATH = "/api/v1/search/clicks";

    @Autowired
    private RestTestClient client;

    @Test
    @DisplayName("Gateway가 붙인 회원 ID 헤더가 있으면 204다")
    void record() {
        client.post().uri(PATH)
            .header(MemberHeaders.MEMBER_ID, "7")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""
                {"keyword": "원피스", "productId": 31, "rank": 3, "sort": "RELEVANCE"}""")
            .exchange()
            .expectStatus().isNoContent()
            .expectBody().isEmpty();
    }

    @Test
    @DisplayName("순위가 범위 밖이면 400 ProblemDetail이다")
    void invalidRank() {
        client.post().uri(PATH)
            .header(MemberHeaders.MEMBER_ID, "7")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""
                {"keyword": "원피스", "productId": 31, "rank": 0}""")
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(400)
            .jsonPath("$.instance").isEqualTo(PATH);
    }

    @Test
    @DisplayName("회원 ID 헤더가 없으면 401 ProblemDetail이다")
    void unauthorized() {
        client.post().uri(PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""
                {"keyword": "원피스", "productId": 31, "rank": 3}""")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(401);
    }
}
