package com.bbcc.kidly.boundedcontext.auth.app;

import com.bbcc.kidly.boundedcontext.auth.app.dto.AuthTokens;
import com.bbcc.kidly.boundedcontext.auth.app.dto.SocialLoginRequest;
import com.bbcc.kidly.boundedcontext.auth.app.port.AccessTokenIssuer;
import com.bbcc.kidly.boundedcontext.auth.app.port.TokenSubject;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthErrorCode;
import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.shared.auth.PrincipalId;
import com.bbcc.kidly.shared.auth.PrincipalType;
import com.bbcc.kidly.shared.member.dto.MemberLoginState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SocialLoginService socialLoginService;
    private final RefreshTokenService refreshTokenService;
    private final AccessTokenIssuer accessTokenIssuer;

    public AuthTokens login(String providerName, SocialLoginRequest request) {
        var state = socialLoginService.signIn(providerName, request);
        if (!(state instanceof MemberLoginState.Active member)) {
            log.warn("로그인 거절: memberId={} reason=login-not-allowed", state.memberId());
            throw new BusinessException(AuthErrorCode.LOGIN_NOT_ALLOWED);
        }
        var refreshToken = refreshTokenService.issue(PrincipalId.member(member.memberId()));
        log.info("로그인: memberId={} provider={}", member.memberId(), providerName);
        return new AuthTokens(accessTokenIssuer.issue(TokenSubject.Member.of(member)), refreshToken);
    }

    public AuthTokens reissue(String refreshToken) {
        var reissued = refreshTokenService.reissue(PrincipalType.MEMBER, refreshToken);
        return new AuthTokens(accessTokenIssuer.issue(reissued.subject()), reissued.refreshToken());
    }

    public void logout(String refreshToken) {
        if (refreshToken != null) {
            refreshTokenService.revoke(PrincipalType.MEMBER, refreshToken);
        }
    }
}
