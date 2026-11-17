package com.bbcc.kidly.boundedcontext.auth.in;

import com.bbcc.kidly.boundedcontext.auth.app.ChildAuthService;
import com.bbcc.kidly.boundedcontext.auth.app.dto.ChildLoginRequest;
import com.bbcc.kidly.boundedcontext.auth.in.dto.LoginResponse;
import com.bbcc.kidly.shared.auth.PrincipalType;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChildAuthController {

    private final ChildAuthService service;
    private final RefreshTokenCookies cookies;

    @PostMapping("/api/v1/auth/child/login")
    public LoginResponse login(@Valid @RequestBody ChildLoginRequest request, HttpServletResponse response) {
        var tokens = service.login(request);
        cookies.issue(response, PrincipalType.CHILD, tokens.refreshToken());
        return new LoginResponse(tokens.accessToken());
    }

    @PostMapping("/api/v1/auth/child/reissue")
    public LoginResponse reissue(@CookieValue(name = RefreshTokenCookies.CHILD_NAME, required = false) String token,
        HttpServletResponse response) {
        var tokens = service.reissue(token);
        cookies.issue(response, PrincipalType.CHILD, tokens.refreshToken());
        return new LoginResponse(tokens.accessToken());
    }

    @PostMapping("/api/v1/auth/child/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = RefreshTokenCookies.CHILD_NAME, required = false) String token,
        HttpServletResponse response) {
        service.logout(token);
        cookies.expire(response, PrincipalType.CHILD);
    }
}
