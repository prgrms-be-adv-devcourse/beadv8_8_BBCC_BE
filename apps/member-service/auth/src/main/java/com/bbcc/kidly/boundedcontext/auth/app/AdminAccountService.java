package com.bbcc.kidly.boundedcontext.auth.app;

import com.bbcc.kidly.boundedcontext.auth.app.dto.AdminResponse;
import com.bbcc.kidly.boundedcontext.auth.domain.AdminAccount;
import com.bbcc.kidly.boundedcontext.auth.domain.AdminAccountRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.AdminPassword;
import com.bbcc.kidly.boundedcontext.auth.domain.AdminStatus;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthErrorCode;
import com.bbcc.kidly.boundedcontext.auth.domain.RefreshTokenRepository;
import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.member.support.error.CommonErrorCode;
import com.bbcc.kidly.shared.auth.PrincipalId;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenRevocationService accessTokenRevocationService;

    @Transactional(readOnly = true)
    public Page<AdminAccount> list(String status, Pageable pageable) {
        var filter = Optional.ofNullable(status).map(AdminStatus::valueOf).orElse(null);
        return adminAccountRepository.findAccounts(filter, pageable);
    }

    @Transactional
    public AdminResponse create(Long actorId, String email, String rawPassword) {
        lockActiveActor(actorId);
        var normalizedEmail = AdminAccount.normalizeEmail(email);
        if (adminAccountRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(AuthErrorCode.ADMIN_EMAIL_ALREADY_USED);
        }
        AdminPassword password;
        try {
            password = new AdminPassword(rawPassword);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(AuthErrorCode.INVALID_ADMIN_PASSWORD, e.getMessage());
        }
        var hash = passwordEncoder.encode(password.value());
        var account = adminAccountRepository.saveAccount(AdminAccount.create(normalizedEmail, hash, actorId));
        return AdminResponse.from(account);
    }

    @Transactional
    public void disable(Long actorId, Long adminId) {
        var target = actorId < adminId
            ? lockActiveActorThenTarget(actorId, adminId)
            : lockTargetThenActiveActor(actorId, adminId);
        var now = LocalDateTime.now();
        if (target.disable(actorId, now)) {
            adminAccountRepository.saveAccount(target);
            var principal = PrincipalId.admin(adminId);
            refreshTokenRepository.deleteByPrincipal(principal);
            accessTokenRevocationService.revoke(principal, now);
        }
    }

    private AdminAccount lockActiveActorThenTarget(Long actorId, Long adminId) {
        lockActiveActor(actorId);
        return lockTarget(adminId);
    }

    private AdminAccount lockTargetThenActiveActor(Long actorId, Long adminId) {
        var target = lockTarget(adminId);
        lockActiveActor(actorId);
        return target;
    }

    private AdminAccount lockTarget(Long adminId) {
        return adminAccountRepository.findByIdForUpdate(adminId)
            .orElseThrow(() -> new BusinessException(AuthErrorCode.ADMIN_NOT_FOUND));
    }

    private void lockActiveActor(Long actorId) {
        adminAccountRepository.findByIdForUpdate(actorId)
            .filter(AdminAccount::isActive)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }
}
