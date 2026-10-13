package com.bbcc.kidly.global.security;

import static com.bbcc.kidly.support.TestApiController.LOGIN_MEMBER_PATH;

import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class LoginMemberApiTest {

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Test
    @DisplayName("토큰의 회원 ID · 닉네임 · 역할을 컨트롤러 파라미터로 받는다")
    void resolvesAuthMember() {
        String token = tokens.seller(7L);

        client.get().uri(LOGIN_MEMBER_PATH)
            .headers(headers -> headers.setBearerAuth(token))
            .exchange()
            .expectStatus().isOk()
            .expectBody(AuthMember.class)
            .isEqualTo(new AuthMember(7L, TestTokens.nickname(7L), List.of(Role.BUYER, Role.SELLER)));
    }
}
