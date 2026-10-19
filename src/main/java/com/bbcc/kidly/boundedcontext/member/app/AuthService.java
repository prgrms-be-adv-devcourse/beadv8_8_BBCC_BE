package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.dto.AuthTokens;
import com.bbcc.kidly.boundedcontext.member.app.dto.SocialLoginRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 로그인 · 재발급 · 로그아웃. Access Token과 Refresh Token을 함께 다룬다.
 * 트랜잭션을 걸지 않는다. 소셜 로그인은 트랜잭션 밖에서 동시 가입 실패 뒤 다시 조회해야 하고,
 * 소셜 제공자 호출이 DB 커넥션을 붙잡지 않아야 한다. 각 단계의 트랜잭션은 하위 서비스가 건다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SocialLoginService socialLoginService;
    private final RefreshTokenService refreshTokenService;
    private final AuthTokenService authTokenService;

    // 처음이면 가입된다
    public AuthTokens login(String providerName, SocialLoginRequest request) {
        var member = socialLoginService.login(providerName, request);
        var refreshToken = refreshTokenService.issue(member.getId());
        log.info("로그인: memberId={} provider={}", member.getId(), providerName);
        return new AuthTokens(authTokenService.issueAccessToken(member), refreshToken);
    }

    // Refresh Token을 새 값으로 바꾸고, 지금 역할로 Access Token을 다시 발급한다
    public AuthTokens reissue(String refreshToken) {
        var reissued = refreshTokenService.reissue(refreshToken);
        return new AuthTokens(authTokenService.issueAccessToken(reissued.member()), reissued.refreshToken());
    }

    // 쿠키가 없으면 지울 토큰이 없다. 이미 폐기된 토큰이어도 그대로 끝난다
    public void logout(String refreshToken) {
        if (refreshToken != null) {
            refreshTokenService.revoke(refreshToken);
        }
    }
}
