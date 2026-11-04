package com.bbcc.kidly.boundedcontext.auth.app;

import com.bbcc.kidly.boundedcontext.auth.app.dto.AdminLoginRequest;
import com.bbcc.kidly.boundedcontext.auth.app.dto.AuthTokens;
import com.bbcc.kidly.boundedcontext.auth.app.port.AccessTokenIssuer;
import com.bbcc.kidly.boundedcontext.auth.app.port.TokenSubject;
import com.bbcc.kidly.boundedcontext.auth.domain.AdminAccount;
import com.bbcc.kidly.boundedcontext.auth.domain.AdminAccountRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthErrorCode;
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
public class AdminAuthService {

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AccessTokenIssuer accessTokenIssuer;
    private final String hashComparedWhenEmailUnknown;

    public AdminAuthService(AdminAccountRepository adminAccountRepository, PasswordEncoder passwordEncoder,
        RefreshTokenService refreshTokenService, AccessTokenIssuer accessTokenIssuer) {
        this.adminAccountRepository = adminAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.accessTokenIssuer = accessTokenIssuer;
        this.hashComparedWhenEmailUnknown = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthTokens login(AdminLoginRequest request) {
        var admin = authenticate(request.email(), request.password());
        return issue(admin);
    }

    AdminAccount authenticate(String email, String password) {
        var found = adminAccountRepository.findByEmailForUpdate(AdminAccount.normalizeEmail(email));
        var passwordHash = found.map(AdminAccount::getPasswordHash).orElse(hashComparedWhenEmailUnknown);
        var matches = passwordEncoder.matches(password, passwordHash);
        if (found.isEmpty()) {
            throw rejected("unknown-email");
        }
        var admin = found.get();
        if (!matches) {
            throw rejected("wrong-password adminId=" + admin.getId());
        }
        if (!admin.isActive()) {
            throw rejected("disabled adminId=" + admin.getId());
        }
        return admin;
    }

    AuthTokens issue(AdminAccount admin) {
        var refreshToken = refreshTokenService.issue(PrincipalId.admin(admin.getId()));
        log.info("관리자 로그인: adminId={}", admin.getId());
        return new AuthTokens(accessTokenIssuer.issue(new TokenSubject.Admin(admin.getId())), refreshToken);
    }

    public AuthTokens reissue(String refreshToken) {
        var reissued = refreshTokenService.reissue(PrincipalType.ADMIN, refreshToken);
        return new AuthTokens(accessTokenIssuer.issue(reissued.subject()), reissued.refreshToken());
    }

    public void logout(String refreshToken) {
        if (refreshToken != null) {
            refreshTokenService.revoke(PrincipalType.ADMIN, refreshToken);
        }
    }

    private static BusinessException rejected(String reason) {
        log.warn("관리자 로그인 거절: {}", reason);
        return new BusinessException(AuthErrorCode.INVALID_ADMIN_CREDENTIALS);
    }
}
