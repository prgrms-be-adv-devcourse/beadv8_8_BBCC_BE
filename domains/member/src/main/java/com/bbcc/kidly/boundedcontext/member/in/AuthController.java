package com.bbcc.kidly.boundedcontext.member.in;

import com.bbcc.kidly.boundedcontext.member.app.AuthService;
import com.bbcc.kidly.boundedcontext.member.app.dto.SocialLoginRequest;
import com.bbcc.kidly.boundedcontext.member.in.dto.LoginResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    // 토큰이 든 응답을 브라우저 · 중간 캐시가 저장하지 않게 한다
    private static final String NO_STORE = CacheControl.noStore().getHeaderValue();

    private final AuthService authService;
    private final RefreshTokenCookies refreshTokenCookies;

    // 프론트가 소셜 제공자에서 받은 인가 코드로 로그인한다. 처음이면 가입된다
    @PostMapping("/api/v1/auth/login/{provider}")
    public LoginResponse login(
        @PathVariable String provider, @Valid @RequestBody SocialLoginRequest request, HttpServletResponse response) {
        var tokens = authService.login(provider, request);
        refreshTokenCookies.add(response, tokens.refreshToken());
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
        return new LoginResponse(tokens.accessToken());
    }

    // Access Token이 만료되면 쿠키의 Refresh Token으로 둘 다 새로 받는다
    @PostMapping("/api/v1/auth/reissue")
    public LoginResponse reissue(
        @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken,
        HttpServletResponse response) {
        var tokens = authService.reissue(refreshToken);
        refreshTokenCookies.add(response, tokens.refreshToken());
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
        return new LoginResponse(tokens.accessToken());
    }

    // 쿠키가 없거나 이미 폐기된 토큰이어도 204로 응답하고 쿠키를 지운다
    @PostMapping("/api/v1/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
        @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken,
        HttpServletResponse response) {
        authService.logout(refreshToken);
        refreshTokenCookies.expire(response);
    }
}
