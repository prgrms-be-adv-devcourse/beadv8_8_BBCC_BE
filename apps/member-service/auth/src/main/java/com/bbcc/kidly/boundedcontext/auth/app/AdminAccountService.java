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
        AdminPassword password;
        try {
            password = new AdminPassword(rawPassword);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(AuthErrorCode.INVALID_ADMIN_PASSWORD, e.getMessage());
        }
        var hash = passwordEncoder.encode(password.value());
        var account = adminAccountRepository.saveAccount(AdminAccount.create(email, hash, actorId));
        return AdminResponse.from(account);
    }

    // 두 관리자가 서로를 동시에 비활성화하면 둘 다 성공해 활성 관리자가 0명이 된다.
    // 두 행을 ID 순서로 잠근 뒤 호출한 관리자가 아직 활성인지 보면 늦게 온 쪽이 막힌다
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

    // 비활성화된 관리자의 Access Token은 무효화가 Gateway에 닿기 전까지 잠깐 통과한다. 그 사이의 요청을 막는다
    private void lockActiveActor(Long actorId) {
        adminAccountRepository.findByIdForUpdate(actorId)
            .filter(AdminAccount::isActive)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }
}
