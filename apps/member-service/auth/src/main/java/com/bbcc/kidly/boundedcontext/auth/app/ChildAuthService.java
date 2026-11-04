package com.bbcc.kidly.boundedcontext.auth.app;

import com.bbcc.kidly.boundedcontext.auth.app.dto.AuthTokens;
import com.bbcc.kidly.boundedcontext.auth.app.dto.ChildLoginRequest;
import com.bbcc.kidly.boundedcontext.auth.app.port.AccessTokenIssuer;
import com.bbcc.kidly.boundedcontext.auth.app.port.TokenSubject;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthErrorCode;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccount;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccountRepository;
import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.shared.auth.PrincipalId;
import com.bbcc.kidly.shared.auth.PrincipalType;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ChildAuthService {
    private final ChildAccountRepository accounts;
    private final PasswordEncoder encoder;
    private final RefreshTokenService refreshTokens;
    private final AccessTokenIssuer issuer;
    private final String dummyHash;

    public ChildAuthService(ChildAccountRepository accounts, PasswordEncoder encoder,
        RefreshTokenService refreshTokens, AccessTokenIssuer issuer) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
        this.issuer = issuer;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthTokens login(ChildLoginRequest request) {
        var found = accounts.findByLoginId(request.loginId());
        var hash = found.map(ChildAccount::getPasswordHash).orElse(dummyHash);
        var matches = encoder.matches(request.password(), hash);
        if (found.isEmpty()) {
            throw rejected("unknown-login-id");
        }
        var accountId = found.get().getId();
        if (!matches) {
            throw rejected("wrong-password childAccountId=" + accountId);
        }
        // BCrypt 비교는 잠그기 전에 한다. 잠금을 쥔 채 비교하면 같은 아이디로 몰린 로그인이 재발급 · 삭제 처리를 붙잡는다
        // 잠근 뒤 다시 읽어, 비교하는 사이 자녀 삭제로 지워진 계정에는 토큰을 주지 않는다
        var child = accounts.findByIdForUpdate(accountId)
            .orElseThrow(() -> rejected("deleted childAccountId=" + accountId));
        var refresh = refreshTokens.issue(PrincipalId.child(child.getId()));
        log.info("자녀 로그인: childAccountId={}", child.getId());
        return new AuthTokens(issuer.issue(new TokenSubject.Child(child.getId(), child.getGuardianId())), refresh);
    }

    public AuthTokens reissue(String refreshToken) {
        var tokens = refreshTokens.reissue(PrincipalType.CHILD, refreshToken);
        return new AuthTokens(issuer.issue(tokens.subject()), tokens.refreshToken());
    }

    public void logout(String refreshToken) {
        if (refreshToken != null) {
            refreshTokens.revoke(PrincipalType.CHILD, refreshToken);
        }
    }

    private static BusinessException rejected(String reason) {
        log.warn("자녀 로그인 거절: {}", reason);
        return new BusinessException(AuthErrorCode.INVALID_CHILD_CREDENTIALS);
    }
}
