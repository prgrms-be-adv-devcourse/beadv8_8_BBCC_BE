package com.bbcc.kidly.boundedcontext.auth.app;

import com.bbcc.kidly.boundedcontext.auth.app.dto.ReissuedToken;
import com.bbcc.kidly.boundedcontext.auth.app.port.TokenSubject;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthErrorCode;
import com.bbcc.kidly.boundedcontext.auth.domain.RefreshToken;
import com.bbcc.kidly.boundedcontext.auth.domain.RefreshTokenRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.auth.PrincipalId;
import com.bbcc.kidly.shared.auth.PrincipalType;
import com.bbcc.kidly.shared.member.dto.MemberLoginState;
import com.bbcc.kidly.shared.member.out.MemberAccounts;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh Token을 발급 · 재발급 · 폐기한다. 원문은 쿠키로만 내려주고 DB에는 해시만 둔다.
 * 주체(회원 · 관리자)마다 토큰 하나이고, 재발급 · 폐기는 엔드포인트가 기대한 주체 종류의 토큰만 받는다.
 * Access Token은 {@link AuthService}가 발급한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberAccounts memberAccounts;
    private final RefreshTokenProperties properties;
    private final AccessTokenRevocationService accessTokenRevocationService;

    // 로그인할 때 발급한다. 이미 있으면 새 값으로 바꾸므로 다른 기기의 토큰은 다음 재발급 때 거절된다
    @Transactional
    public String issue(PrincipalId principal) {
        var now = LocalDateTime.now();
        var token = RefreshToken.generate();
        refreshTokenRepository.upsert(principal, RefreshToken.hashOf(token), now.plus(properties.validity()), now);
        return token;
    }

    // 토큰을 새 값으로 바꾼다. 같은 토큰으로 동시에 요청하면 한 요청만 바꾸고 나머지는 거절한다
    // 행의 주체 종류가 expected와 다르면(회원 쿠키 값으로 관리자 재발급) 거절한다
    // 재사용을 감지해 지운 것은 401을 던져도 남아야 해서 업무 예외로는 롤백하지 않는다
    @Transactional(noRollbackFor = BusinessException.class)
    public ReissuedToken reissue(PrincipalType expected, String token) {
        if (token == null) {
            throw invalid();
        }
        var now = LocalDateTime.now();
        var tokenHash = RefreshToken.hashOf(token);
        var current = refreshTokenRepository.findByTokenHash(tokenHash)
            .filter(refreshToken -> refreshToken.isUsable(now));
        if (current.isEmpty()) {
            revokeIfReused(tokenHash, now);
            throw invalid();
        }
        var saved = current.get();
        var principal = saved.principal();
        if (principal.type() != expected) {
            log.warn("Refresh Token 재발급 거절: type={} id={} expected={} reason=principal-type-mismatch",
                principal.type(), principal.id(), expected);
            throw invalid();
        }
        var subject = currentSubject(principal).orElseThrow(RefreshTokenService::invalid);
        var newToken = RefreshToken.generate();
        var rotated = refreshTokenRepository.rotate(
            principal, saved.getTokenHash(), RefreshToken.hashOf(newToken), now.plus(properties.validity()), now);
        if (rotated != 1) {
            log.warn("Refresh Token 재발급 거절: type={} id={} reason=concurrent-rotation",
                principal.type(), principal.id());
            throw invalid();
        }
        return new ReissuedToken(subject, newToken);
    }

    // 주체 종류별로 지금 토큰을 줘도 되는지 본다
    private Optional<TokenSubject> currentSubject(PrincipalId principal) {
        return switch (principal.type()) {
            case MEMBER -> memberAccounts.findLoginState(principal.id())
                .filter(MemberLoginState.Active.class::isInstance)
                .map(MemberLoginState.Active.class::cast)
                .map(TokenSubject.Member::of);
            case ADMIN -> Optional.empty();
        };
    }

    // 이미 교체된 직전 토큰이 유예 시간 뒤에 오면 탈취로 보고 그 주체의 Refresh Token을 지우고 Access Token도 무효화한다
    private void revokeIfReused(String tokenHash, LocalDateTime now) {
        refreshTokenRepository.findByPreviousTokenHash(tokenHash)
            .filter(refreshToken -> refreshToken.isReused(now))
            .map(RefreshToken::principal)
            .ifPresent(principal -> {
                refreshTokenRepository.deleteByPrincipal(principal);
                accessTokenRevocationService.revoke(principal, now);
                log.warn("Refresh Token 재사용 감지로 폐기: type={} id={}", principal.type(), principal.id());
            });
    }

    // 행의 주체 종류가 expected와 다르면(회원 쿠키 값으로 관리자 로그아웃) 지우지 않는다
    @Transactional
    public void revoke(PrincipalType expected, String token) {
        var tokenHash = RefreshToken.hashOf(token);
        refreshTokenRepository.findByTokenHash(tokenHash)
            .filter(refreshToken -> refreshToken.principal().type() == expected)
            .ifPresent(refreshToken -> refreshTokenRepository.deleteByTokenHash(tokenHash));
    }

    private static BusinessException invalid() {
        return new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
}
