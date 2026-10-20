package com.bbcc.kidly.global.security;

import static com.bbcc.kidly.support.TestApiController.AUTH_PATH;
import static com.bbcc.kidly.support.TestApiController.PUBLIC_PATH;

import com.bbcc.kidly.boundedcontext.member.app.AccessTokenRevocationService;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;

@FunctionalTest
class RevokedAccessTokenApiTest {

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private AccessTokenRevocationService revocationService;

    @Test
    @DisplayName("무효화된 회원의 토큰이면 401 ProblemDetail로 응답한다")
    void revokedToken() {
        var token = tokens.buyer(1L);
        revoke(1L);

        expectUnauthorized(AUTH_PATH, token);
    }

    @Test
    @DisplayName("다른 회원의 토큰은 계속 쓸 수 있다")
    void otherMemberToken() {
        var token = tokens.buyer(2L);
        revoke(1L);

        client.get().uri(AUTH_PATH)
            .headers(headers -> headers.setBearerAuth(token))
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("공개 API에 보낸 토큰도 무효화됐으면 401 ProblemDetail로 응답한다")
    void revokedTokenOnPublicApi() {
        var token = tokens.buyer(1L);
        revoke(1L);

        expectUnauthorized(PUBLIC_PATH, token);
    }

    private void revoke(Long memberId) {
        transactionTemplate.executeWithoutResult(status -> revocationService.revoke(memberId, LocalDateTime.now()));
    }

    private void expectUnauthorized(String path, String token) {
        client.get().uri(path)
            .headers(headers -> headers.setBearerAuth(token))
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("로그인이 필요합니다")
            .jsonPath("$.status").isEqualTo(401)
            .jsonPath("$.instance").isEqualTo(path);
    }
}
