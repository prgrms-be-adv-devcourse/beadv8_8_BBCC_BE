package com.bbcc.kidly.boundedcontext.member.in;

import com.bbcc.kidly.boundedcontext.member.app.AuthTokenService;
import com.bbcc.kidly.boundedcontext.member.app.RefreshTokenService;
import com.bbcc.kidly.boundedcontext.member.app.SocialLoginService;
import com.bbcc.kidly.boundedcontext.member.app.dto.SocialLoginRequest;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.bbcc.kidly.boundedcontext.member.in.dto.LoginResponse;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.security.PublicApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final SocialLoginService socialLoginService;
    private final AuthTokenService authTokenService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCookies refreshTokenCookies;

    // 프론트가 소셜 제공자에서 받은 인가 코드로 로그인한다. 처음이면 가입된다
    @PublicApi
    @PostMapping("/api/v1/auth/login/{provider}")
    public ResponseEntity<LoginResponse> login(
        @PathVariable String provider, @Valid @RequestBody SocialLoginRequest request) {
        var member = socialLoginService.login(OAuthProvider.from(provider), request);
        return withTokens(member.getId(), refreshTokenService.issue(member.getId()));
    }

    // Access Token이 만료되면 쿠키의 Refresh Token으로 둘 다 새로 받는다
    @PublicApi
    @PostMapping("/api/v1/auth/reissue")
    public ResponseEntity<LoginResponse> reissue(
        @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new BusinessException(MemberErrorCode.INVALID_REFRESH_TOKEN);
        }
        var reissued = refreshTokenService.reissue(refreshToken);
        return withTokens(reissued.memberId(), reissued.refreshToken());
    }

    // 쿠키가 없거나 이미 폐기된 토큰이어도 204로 응답하고 쿠키를 지운다
    @PublicApi
    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout(
        @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
        if (refreshToken != null) {
            refreshTokenService.revoke(refreshToken);
        }
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.expired().toString())
            .build();
    }

    private ResponseEntity<LoginResponse> withTokens(Long memberId, String refreshToken) {
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.of(refreshToken).toString())
            .body(new LoginResponse(authTokenService.issueAccessToken(memberId)));
    }
}
