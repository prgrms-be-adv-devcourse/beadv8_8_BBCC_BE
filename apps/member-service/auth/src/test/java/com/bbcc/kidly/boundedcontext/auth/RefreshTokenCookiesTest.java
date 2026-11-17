package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.app.RefreshTokenProperties;
import com.bbcc.kidly.boundedcontext.auth.in.RefreshTokenCookies;
import com.bbcc.kidly.boundedcontext.auth.in.SecurityProperties;
import com.bbcc.kidly.shared.auth.PrincipalType;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

class RefreshTokenCookiesTest {

    @Test
    @DisplayName("회원 쿠키를 발급하면 이름 · Path · HttpOnly · SameSite · Secure · Max-Age를 싣는다")
    void addsMemberCookie() {
        var cookies = cookies(true);
        var response = new MockHttpServletResponse();

        cookies.issue(response, PrincipalType.MEMBER, "token-value");

        var setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
            .contains("refresh_token=token-value")
            .contains("Path=/api/v1/auth;")
            .contains("HttpOnly")
            .contains("SameSite=Lax")
            .contains("Secure")
            .contains("Max-Age=1209600");
    }

    @Test
    @DisplayName("쿠키를 발급한 응답은 캐시하지 않는다")
    void issuedResponseIsNotCached() {
        var cookies = cookies(true);
        var response = new MockHttpServletResponse();

        cookies.issue(response, PrincipalType.CHILD, "token");

        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    }

    @Test
    @DisplayName("관리자 쿠키는 이름이 admin_refresh_token이고 Path가 /api/v1/auth/admin이다")
    void addsAdminCookie() {
        var cookies = cookies(true);
        var response = new MockHttpServletResponse();

        cookies.issue(response, PrincipalType.ADMIN, "token-value");

        var setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
            .startsWith("admin_refresh_token=token-value")
            .contains("Path=/api/v1/auth/admin")
            .contains("HttpOnly")
            .contains("SameSite=Lax")
            .contains("Secure")
            .contains("Max-Age=1209600");
    }

    @Test
    void addsChildCookie() {
        var cookies = cookies(true);
        var response = new MockHttpServletResponse();
        cookies.issue(response, PrincipalType.CHILD, "token");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
            .startsWith("child_refresh_token=token").contains("Path=/api/v1/auth/child");
    }

    @Test
    @DisplayName("Secure 설정이 꺼져 있으면 Secure 속성을 싣지 않는다")
    void respectsSecureCookieSetting() {
        var cookies = cookies(false);
        var response = new MockHttpServletResponse();

        cookies.issue(response, PrincipalType.MEMBER, "token-value");

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).doesNotContain("Secure");
    }

    @Test
    @DisplayName("쿠키를 지우면 그 주체의 이름 · Path에 Max-Age=0을 싣는다")
    void expiresCookie() {
        var cookies = cookies(true);
        var memberResponse = new MockHttpServletResponse();
        var adminResponse = new MockHttpServletResponse();

        cookies.expire(memberResponse, PrincipalType.MEMBER);
        cookies.expire(adminResponse, PrincipalType.ADMIN);

        assertThat(memberResponse.getHeader(HttpHeaders.SET_COOKIE))
            .startsWith("refresh_token=;")
            .contains("Path=/api/v1/auth;")
            .contains("Max-Age=0");
        assertThat(adminResponse.getHeader(HttpHeaders.SET_COOKIE))
            .startsWith("admin_refresh_token=;")
            .contains("Path=/api/v1/auth/admin")
            .contains("Max-Age=0");
    }

    private static RefreshTokenCookies cookies(boolean secure) {
        return new RefreshTokenCookies(new SecurityProperties(secure), new RefreshTokenProperties(Duration.ofDays(14)));
    }
}
