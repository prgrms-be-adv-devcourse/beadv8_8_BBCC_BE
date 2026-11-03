package com.bbcc.kidly.boundedcontext.auth.in;

import com.bbcc.kidly.boundedcontext.auth.app.AdminAuthService;
import com.bbcc.kidly.boundedcontext.auth.app.RefreshTokenProperties;
import com.bbcc.kidly.boundedcontext.auth.app.dto.AdminLoginRequest;
import com.bbcc.kidly.boundedcontext.auth.in.dto.LoginResponse;
import com.bbcc.kidly.global.security.RefreshTokenCookies;
import com.bbcc.kidly.shared.auth.PrincipalType;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminAuthController {

    private static final String NO_STORE = CacheControl.noStore().getHeaderValue();

    private final AdminAuthService adminAuthService;
    private final RefreshTokenCookies refreshTokenCookies;
    private final RefreshTokenProperties refreshTokenProperties;

    @PostMapping("/api/v1/auth/admin/login")
    public LoginResponse login(@Valid @RequestBody AdminLoginRequest request, HttpServletResponse response) {
        var tokens = adminAuthService.login(request);
        refreshTokenCookies.add(
            response, PrincipalType.ADMIN, tokens.refreshToken(), refreshTokenProperties.validity());
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
        return new LoginResponse(tokens.accessToken());
    }

    @PostMapping("/api/v1/auth/admin/reissue")
    public LoginResponse reissue(
        @CookieValue(name = RefreshTokenCookies.ADMIN_NAME, required = false) String refreshToken,
        HttpServletResponse response) {
        var tokens = adminAuthService.reissue(refreshToken);
        refreshTokenCookies.add(
            response, PrincipalType.ADMIN, tokens.refreshToken(), refreshTokenProperties.validity());
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
        return new LoginResponse(tokens.accessToken());
    }

    @PostMapping("/api/v1/auth/admin/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
        @CookieValue(name = RefreshTokenCookies.ADMIN_NAME, required = false) String refreshToken,
        HttpServletResponse response) {
        adminAuthService.logout(refreshToken);
        refreshTokenCookies.expire(response, PrincipalType.ADMIN);
    }
}
