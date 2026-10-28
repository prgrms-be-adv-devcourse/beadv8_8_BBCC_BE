package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.app.dto.SocialLoginRequest;
import com.bbcc.kidly.boundedcontext.member.in.dto.LoginResponse;
import com.bbcc.kidly.support.FunctionalTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

@FunctionalTest
class RefreshTokenApiTest {

    private static final String COOKIE = "refresh_token";
    private static final String LOGIN_PATH = "/api/v1/auth/login/kakao";
    private static final String REISSUE_PATH = "/api/v1/auth/reissue";
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";

    @Autowired
    private RestTestClient client;

    @Test
    @DisplayName("로그인하면 Refresh Token을 HttpOnly 쿠키로 받고 응답은 캐시되지 않는다")
    void loginSetsCookie() {
        client.post().uri(LOGIN_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new SocialLoginRequest("kakao-1"))
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueMatches(HttpHeaders.CACHE_CONTROL, ".*no-store.*")
            .expectCookie().httpOnly(COOKIE, true)
            .expectCookie().secure(COOKIE, true)
            .expectCookie().sameSite(COOKIE, "Lax")
            .expectCookie().path(COOKIE, "/api/v1/auth")
            .expectCookie().maxAge(COOKIE, Duration.ofDays(14))
            .expectCookie().domain(COOKIE, (String) null);
    }

    @Test
    @DisplayName("쿠키로 재발급하면 새 Access Token과 새 쿠키를 받는다")
    void reissue() {
        var oldToken = login();

        var result = reissue(oldToken)
            .expectStatus().isOk()
            .expectHeader().valueMatches(HttpHeaders.CACHE_CONTROL, ".*no-store.*")
            .expectCookie().httpOnly(COOKIE, true)
            .returnResult(LoginResponse.class);

        assertThat(result.getResponseBody().accessToken()).isNotBlank();
        assertThat(result.getResponseCookies().getFirst(COOKIE).getValue()).isNotEqualTo(oldToken);
    }

    @Test
    @DisplayName("이미 재발급에 쓴 쿠키로는 다시 재발급할 수 없다")
    void reusedToken() {
        var oldToken = login();
        reissue(oldToken).expectStatus().isOk();

        expectInvalid(reissue(oldToken));
    }

    @Test
    @DisplayName("쿠키 없이 재발급하면 401")
    void reissueWithoutCookie() {
        expectInvalid(client.post().uri(REISSUE_PATH).exchange());
    }

    @Test
    @DisplayName("로그아웃하면 쿠키를 지우고, 그 토큰으로는 재발급할 수 없다")
    void logout() {
        var token = login();

        client.post().uri(LOGOUT_PATH)
            .cookie(COOKIE, token)
            .exchange()
            .expectStatus().isNoContent()
            .expectCookie().maxAge(COOKIE, Duration.ZERO)
            .expectCookie().path(COOKIE, "/api/v1/auth");

        expectInvalid(reissue(token));
    }

    @Test
    @DisplayName("쿠키 없이 로그아웃해도 204")
    void logoutWithoutCookie() {
        client.post().uri(LOGOUT_PATH)
            .exchange()
            .expectStatus().isNoContent()
            .expectCookie().maxAge(COOKIE, Duration.ZERO);
    }

    // 로그인 응답의 Refresh Token 쿠키 값을 돌려준다
    private String login() {
        return client.post().uri(LOGIN_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new SocialLoginRequest("kakao-1"))
            .exchange()
            .expectStatus().isOk()
            .returnResult()
            .getResponseCookies()
            .getFirst(COOKIE)
            .getValue();
    }

    private ResponseSpec reissue(String token) {
        return client.post().uri(REISSUE_PATH).cookie(COOKIE, token).exchange();
    }

    private void expectInvalid(ResponseSpec response) {
        response
            .expectStatus().isUnauthorized()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("로그인이 만료되었습니다");
    }
}
