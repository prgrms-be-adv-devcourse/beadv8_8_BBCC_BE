package com.bbcc.kidly.boundedcontext.auth.in;

import com.bbcc.kidly.boundedcontext.auth.app.AuthService;
import com.bbcc.kidly.boundedcontext.auth.app.dto.SocialLoginRequest;
import com.bbcc.kidly.boundedcontext.auth.in.dto.LoginResponse;
import com.bbcc.kidly.shared.auth.PrincipalType;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    private final AuthService authService;
    private final RefreshTokenCookies refreshTokenCookies;

    @PostMapping("/api/v1/auth/login/{provider}")
    public LoginResponse login(
        @PathVariable String provider, @Valid @RequestBody SocialLoginRequest request, HttpServletResponse response) {
        var tokens = authService.login(provider, request);
        refreshTokenCookies.issue(response, PrincipalType.MEMBER, tokens.refreshToken());
        return new LoginResponse(tokens.accessToken());
    }

    @PostMapping("/api/v1/auth/reissue")
    public LoginResponse reissue(
        @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken,
        HttpServletResponse response) {
        var tokens = authService.reissue(refreshToken);
        refreshTokenCookies.issue(response, PrincipalType.MEMBER, tokens.refreshToken());
        return new LoginResponse(tokens.accessToken());
    }

    @PostMapping("/api/v1/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
        @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken,
        HttpServletResponse response) {
        authService.logout(refreshToken);
        refreshTokenCookies.expire(response, PrincipalType.MEMBER);
    }
}
