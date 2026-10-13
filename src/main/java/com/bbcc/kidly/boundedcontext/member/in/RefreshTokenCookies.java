package com.bbcc.kidly.boundedcontext.member.in;

import com.bbcc.kidly.boundedcontext.member.app.RefreshTokenProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseCookie.ResponseCookieBuilder;
import org.springframework.stereotype.Component;

/**
 * Refresh Token 쿠키를 만든다. 스크립트가 읽지 못하게 HttpOnly, 인증 API에만 실리게 Path를 좁힌다.
 * Domain을 정하지 않아 쿠키를 발급한 API 서버에만 전송된다. SameSite=Lax라 다른 사이트의 POST에는 실리지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookies {

    public static final String NAME = "refresh_token";
    private static final String PATH = "/api/v1/auth";

    private final RefreshTokenProperties properties;

    public ResponseCookie of(String token) {
        return builder(token).maxAge(properties.validity()).build();
    }

    // 같은 이름 · Path에 Max-Age=0을 보내면 브라우저가 쿠키를 지운다
    public ResponseCookie expired() {
        return builder("").maxAge(0).build();
    }

    private ResponseCookieBuilder builder(String value) {
        return ResponseCookie.from(NAME, value)
            .httpOnly(true)
            .secure(properties.secureCookie())
            .sameSite("Lax")
            .path(PATH);
    }
}
