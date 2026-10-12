package com.bbcc.kidly.boundedcontext.member;

import static com.bbcc.kidly.support.TestApiController.LOGIN_MEMBER_PATH;
import static com.bbcc.kidly.support.TestApiController.SELLER_PATH;

import com.bbcc.kidly.boundedcontext.member.in.dto.DevTokenResponse;
import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class DevTokenApiTest {

    private static final String DEV_TOKENS_PATH = "/api/v1/dev/tokens";

    @Autowired
    private RestTestClient client;

    @Test
    @DisplayName("발급받은 토큰으로 요청하면 그 회원으로 로그인된다")
    void issuedTokenLogsInAsMember() {
        String token = issue("{\"memberId\": 7, \"roles\": [\"BUYER\"]}");

        client.get().uri(LOGIN_MEMBER_PATH)
            .headers(headers -> headers.setBearerAuth(token))
            .exchange()
            .expectStatus().isOk()
            .expectBody(Long.class).isEqualTo(7L);
    }

    @Test
    @DisplayName("발급받은 토큰에는 요청한 역할이 담긴다")
    void issuedTokenHasRequestedRoles() {
        String token = issue("{\"memberId\": 1, \"roles\": [\"BUYER\", \"SELLER\"]}");

        client.get().uri(SELLER_PATH)
            .headers(headers -> headers.setBearerAuth(token))
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("허용하지 않는 역할이면 400 ProblemDetail로 응답한다")
    void unknownRole() {
        expectBadRequest("{\"memberId\": 1, \"roles\": [\"KING\"]}");
    }

    @Test
    @DisplayName("역할이 비어 있으면 400 ProblemDetail로 응답한다")
    void emptyRoles() {
        expectBadRequest("{\"memberId\": 1, \"roles\": []}");
    }

    @Test
    @DisplayName("회원 ID가 없으면 400 ProblemDetail로 응답한다")
    void missingMemberId() {
        expectBadRequest("{\"roles\": [\"BUYER\"]}");
    }

    private String issue(String body) {
        return client.post().uri(DEV_TOKENS_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isOk()
            .expectBody(DevTokenResponse.class)
            .returnResult()
            .getResponseBody()
            .accessToken();
    }

    private void expectBadRequest(String body) {
        client.post().uri(DEV_TOKENS_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("잘못된 요청입니다")
            .jsonPath("$.status").isEqualTo(400)
            .jsonPath("$.instance").isEqualTo(DEV_TOKENS_PATH);
    }
}
