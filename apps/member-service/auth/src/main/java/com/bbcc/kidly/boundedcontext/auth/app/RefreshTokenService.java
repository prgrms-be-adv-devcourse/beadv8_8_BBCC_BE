package com.bbcc.kidly.boundedcontext.auth.app;

import com.bbcc.kidly.boundedcontext.auth.app.dto.ReissuedToken;
import com.bbcc.kidly.boundedcontext.auth.app.port.TokenSubject;
import com.bbcc.kidly.boundedcontext.auth.domain.AdminAccount;
import com.bbcc.kidly.boundedcontext.auth.domain.AdminAccountRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthErrorCode;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccountRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.RefreshToken;
import com.bbcc.kidly.boundedcontext.auth.domain.RefreshTokenRepository;
import com.bbcc.kidly.member.support.error.BusinessException;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberAccounts memberAccounts;
    private final AdminAccountRepository adminAccountRepository;
    private final ChildAccountRepository childAccountRepository;
    private final RefreshTokenProperties properties;
    private final AccessTokenRevocationService accessTokenRevocationService;

    @Transactional
    public String issue(PrincipalId principal) {
        var now = LocalDateTime.now();
        var token = RefreshToken.generate();
        refreshTokenRepository.upsert(principal, RefreshToken.hashOf(token), now.plus(properties.validity()), now);
        return token;
    }

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

    private Optional<TokenSubject> currentSubject(PrincipalId principal) {
        return switch (principal.type()) {
            case MEMBER -> memberAccounts.findLoginState(principal.id())
                .filter(MemberLoginState.Active.class::isInstance)
                .map(MemberLoginState.Active.class::cast)
                .map(TokenSubject.Member::of);
            case ADMIN -> adminAccountRepository.findByIdForUpdate(principal.id())
                .filter(AdminAccount::isActive)
                .map(admin -> new TokenSubject.Admin(admin.getId()));
            case CHILD -> childAccountRepository.findByIdForUpdate(principal.id())
                .map(child -> new TokenSubject.Child(child.getId(), child.getGuardianId()));
        };
    }

    private void revokeIfReused(String tokenHash, LocalDateTime now) {
        refreshTokenRepository.findByPreviousTokenHash(tokenHash)
            .filter(refreshToken -> refreshToken.isReused(now))
            .map(RefreshToken::principal)
            .ifPresent(principal -> {
                revokeAllTokens(principal, now);
                log.warn("Refresh Token 재사용 감지로 폐기: type={} id={}", principal.type(), principal.id());
            });
    }

    @Transactional
    public void revokeAllTokens(PrincipalId principal, LocalDateTime now) {
        refreshTokenRepository.deleteByPrincipal(principal);
        accessTokenRevocationService.revoke(principal, now);
    }

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
