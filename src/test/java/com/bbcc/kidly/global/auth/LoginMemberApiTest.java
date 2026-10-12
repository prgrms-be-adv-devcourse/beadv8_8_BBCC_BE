package com.bbcc.kidly.global.auth;

import static com.bbcc.kidly.support.TestApiController.LOGIN_MEMBER_PATH;

import com.bbcc.kidly.boundedcontext.member.out.AccessTokenIssuer;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class LoginMemberApiTest {

    @Autowired
    private RestTestClient client;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Test
    @DisplayName("토큰의 회원 ID를 컨트롤러 파라미터로 받는다")
    void resolvesMemberId() {
        String token = accessTokenIssuer.issue(7L, List.of("BUYER"));

        client.get().uri(LOGIN_MEMBER_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .exchange()
            .expectStatus().isOk()
            .expectBody(Long.class).isEqualTo(7L);
    }
}
