package com.bbcc.kidly.boundedcontext.auth.in;

import com.bbcc.kidly.shared.auth.PrincipalType;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.server.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseCookie.ResponseCookieBuilder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenCookies {

    public static final String NAME = "refresh_token";
    public static final String ADMIN_NAME = "admin_refresh_token";
    public static final String CHILD_NAME = "child_refresh_token";

    private final SecurityProperties properties;

    public void add(HttpServletResponse response, PrincipalType type, String token, Duration maxAge) {
        write(response, builder(type, token).maxAge(maxAge));
    }

    // 같은 이름 · Path에 Max-Age=0을 보내면 브라우저가 쿠키를 지운다
    public void expire(HttpServletResponse response, PrincipalType type) {
        write(response, builder(type, "").maxAge(0));
    }

    private ResponseCookieBuilder builder(PrincipalType type, String value) {
        var spec = specOf(type);
        return ResponseCookie.from(spec.name(), value)
            .httpOnly(true)
            .secure(properties.secureCookie())
            .sameSite(Cookie.SameSite.LAX.attributeValue())
            .path(spec.path());
    }

    private static Spec specOf(PrincipalType type) {
        return switch (type) {
            case MEMBER -> new Spec(NAME, "/api/v1/auth");
            case ADMIN -> new Spec(ADMIN_NAME, "/api/v1/auth/admin");
            case CHILD -> new Spec(CHILD_NAME, "/api/v1/auth/child");
        };
    }

    private void write(HttpServletResponse response, ResponseCookieBuilder cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());
    }

    private record Spec(String name, String path) {
    }
}
