package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.in.dto.DevTokenResponse;
import com.bbcc.kidly.shared.auth.Role;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.IssuedAccessToken;
import java.time.Instant;
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
    @DisplayName("발급받은 토큰은 요청한 회원 ID로 서명되고 만료 시각이 있다")
    void issuedTokenHasMemberId() {
        var token = issue("{\"memberId\": 7, \"roles\": [\"BUYER\"]}");

        assertThat(token.memberId()).isEqualTo(7L);
        assertThat(token.expiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("발급받은 토큰에는 요청한 역할이 담긴다")
    void issuedTokenHasRequestedRoles() {
        var token = issue("{\"memberId\": 1, \"roles\": [\"BUYER\", \"SELLER\"]}");

        assertThat(token.roles()).containsExactly(Role.BUYER, Role.SELLER);
    }

    @Test
    @DisplayName("발급받은 토큰에는 요청한 닉네임이 담긴다")
    void issuedTokenHasNickname() {
        var token = issue("{\"memberId\": 7, \"nickname\": \"개발자\", \"roles\": [\"BUYER\"]}");

        assertThat(token.nickname()).isEqualTo("개발자");
    }

    @Test
    @DisplayName("닉네임을 생략하면 회원 ID로 만든 기본 닉네임이 담긴다")
    void defaultNickname() {
        var token = issue("{\"memberId\": 7, \"roles\": [\"BUYER\"]}");

        assertThat(token.nickname()).isEqualTo("회원7");
    }

    @Test
    @DisplayName("닉네임이 비어 있으면 회원 ID로 만든 기본 닉네임이 담긴다")
    void blankNickname() {
        var token = issue("{\"memberId\": 7, \"nickname\": \"\", \"roles\": [\"BUYER\"]}");

        assertThat(token.nickname()).isEqualTo("회원7");
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

    private IssuedAccessToken issue(String body) {
        var accessToken = client.post().uri(DEV_TOKENS_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isOk()
            .expectBody(DevTokenResponse.class)
            .returnResult()
            .getResponseBody()
            .accessToken();
        return IssuedAccessToken.decode(accessToken);
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
