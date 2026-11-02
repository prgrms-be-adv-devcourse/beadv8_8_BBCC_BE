package com.bbcc.kidly.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

class RefreshTokenCookiesTest {

    @Test
    @DisplayName("쿠키를 추가하면 이름 · Path · HttpOnly · SameSite · Secure · Max-Age를 싣는다")
    void addsCookie() {
        var cookies = new RefreshTokenCookies(new SecurityProperties(true));
        var response = new MockHttpServletResponse();

        cookies.add(response, "token-value", Duration.ofDays(14));

        var setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
            .contains("refresh_token=token-value")
            .contains("Path=/api/v1/auth")
            .contains("HttpOnly")
            .contains("SameSite=Lax")
            .contains("Secure")
            .contains("Max-Age=1209600");
    }

    @Test
    @DisplayName("Secure 설정이 꺼져 있으면 Secure 속성을 싣지 않는다")
    void respectsSecureCookieSetting() {
        var cookies = new RefreshTokenCookies(new SecurityProperties(false));
        var response = new MockHttpServletResponse();

        cookies.add(response, "token-value", Duration.ofDays(14));

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).doesNotContain("Secure");
    }

    @Test
    @DisplayName("쿠키를 지우면 같은 이름 · Path에 Max-Age=0을 싣는다")
    void expiresCookie() {
        var cookies = new RefreshTokenCookies(new SecurityProperties(true));
        var response = new MockHttpServletResponse();

        cookies.expire(response);

        var setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
            .contains("refresh_token=")
            .contains("Path=/api/v1/auth")
            .contains("Max-Age=0");
    }
}
