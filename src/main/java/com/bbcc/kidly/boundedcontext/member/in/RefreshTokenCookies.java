package com.bbcc.kidly.boundedcontext.member.in;

import com.bbcc.kidly.boundedcontext.member.app.RefreshTokenProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.server.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseCookie.ResponseCookieBuilder;
import org.springframework.stereotype.Component;

/**
 * Refresh Token 쿠키를 응답에 싣는다. 스크립트가 읽지 못하게 HttpOnly, 인증 API에만 실리게 Path를 좁힌다.
 * Domain을 정하지 않아 쿠키를 발급한 API 서버에만 전송된다. SameSite=Lax라 다른 사이트의 POST에는 실리지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookies {

    public static final String NAME = "refresh_token";
    private static final String PATH = "/api/v1/auth";

    private final RefreshTokenProperties properties;

    public void add(HttpServletResponse response, String token) {
        write(response, builder(token).maxAge(properties.validity()));
    }

    // 같은 이름 · Path에 Max-Age=0을 보내면 브라우저가 쿠키를 지운다
    public void expire(HttpServletResponse response) {
        write(response, builder("").maxAge(0));
    }

    private ResponseCookieBuilder builder(String value) {
        return ResponseCookie.from(NAME, value)
            .httpOnly(true)
            .secure(properties.secureCookie())
            .sameSite(Cookie.SameSite.LAX.attributeValue())
            .path(PATH);
    }

    private void write(HttpServletResponse response, ResponseCookieBuilder cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());
    }
}
