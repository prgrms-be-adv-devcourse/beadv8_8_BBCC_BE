package com.bbcc.kidly.global.security;

import static com.bbcc.kidly.support.TestApiController.LOGIN_MEMBER_PATH;
import static com.bbcc.kidly.support.TestApiController.PUBLIC_PATH;

import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class LoginMemberApiTest {

    @Autowired
    private RestTestClient client;

    @Test
    @DisplayName("회원 헤더의 회원 ID · 닉네임을 컨트롤러 파라미터로 받는다")
    void resolvesAuthMember() {
        client.get().uri(LOGIN_MEMBER_PATH)
            .headers(TestMembers.seller(7L))
            .exchange()
            .expectStatus().isOk()
            .expectBody(AuthMember.class)
            .isEqualTo(new AuthMember(7L, TestMembers.nickname(7L)));
    }

    @Test
    @DisplayName("@LoginMember를 받는 API를 회원 헤더 없이 호출하면 401 ProblemDetail로 응답한다")
    void unauthorizedWithoutMemberHeader() {
        client.get().uri(LOGIN_MEMBER_PATH)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").isEqualTo("about:blank")
            .jsonPath("$.title").isEqualTo("로그인이 필요합니다")
            .jsonPath("$.status").isEqualTo(401)
            .jsonPath("$.detail").isEqualTo("로그인 후 다시 시도해 주세요.")
            .jsonPath("$.instance").isEqualTo(LOGIN_MEMBER_PATH);
    }

    @Test
    @DisplayName("@LoginMember를 받지 않는 API는 회원 헤더 없이 호출할 수 있다")
    void publicApiWithoutMemberHeader() {
        client.get().uri(PUBLIC_PATH)
            .exchange()
            .expectStatus().isOk();
    }
}
