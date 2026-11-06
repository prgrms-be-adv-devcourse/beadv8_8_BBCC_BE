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
        var child = lockIfPasswordUnchanged(matchWithoutLock(request));
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

    private ChildAccount matchWithoutLock(ChildLoginRequest request) {
        var found = accounts.findByLoginId(request.loginId());
        var matches = encoder.matches(request.password(), found.map(ChildAccount::getPasswordHash).orElse(dummyHash));
        if (found.isEmpty()) {
            throw rejected("unknown-login-id");
        }
        if (!matches) {
            throw rejected("wrong-password childAccountId=" + found.get().getId());
        }
        return found.get();
    }

    private ChildAccount lockIfPasswordUnchanged(ChildAccount account) {
        return accounts.findByIdAndPasswordHashForUpdate(account.getId(), account.getPasswordHash())
            .orElseThrow(() -> rejected("deleted-or-password-changed childAccountId=" + account.getId()));
    }

    private static BusinessException rejected(String reason) {
        log.warn("자녀 로그인 거절: {}", reason);
        return new BusinessException(AuthErrorCode.INVALID_CHILD_CREDENTIALS);
    }
}
