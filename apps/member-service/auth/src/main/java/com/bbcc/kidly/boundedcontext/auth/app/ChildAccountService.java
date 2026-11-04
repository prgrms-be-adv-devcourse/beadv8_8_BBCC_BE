package com.bbcc.kidly.boundedcontext.auth.app;

import com.bbcc.kidly.boundedcontext.auth.app.dto.ChildAccountCreateRequest;
import com.bbcc.kidly.boundedcontext.auth.app.dto.ChildAccountResponse;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthErrorCode;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccount;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccountRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildPassword;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildProfileReplicaRepository;
import com.bbcc.kidly.member.support.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChildAccountService {
    private final ChildProfileReplicaRepository replicas;
    private final ChildAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public ChildAccountResponse create(Long guardianId, ChildAccountCreateRequest request) {
        replicas.findByIdForUpdate(request.childProfileId()).filter(r -> r.getGuardianId().equals(guardianId))
            .orElseThrow(() -> new BusinessException(AuthErrorCode.CHILD_PROFILE_NOT_FOUND));
        if (accounts.existsByChildProfileId(request.childProfileId())) {
            throw new BusinessException(AuthErrorCode.CHILD_ACCOUNT_ALREADY_EXISTS);
        }
        if (accounts.existsByLoginId(request.loginId())) {
            throw new BusinessException(AuthErrorCode.CHILD_LOGIN_ID_TAKEN);
        }
        ChildPassword password;
        try {
            password = new ChildPassword(request.password());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(AuthErrorCode.INVALID_CHILD_PASSWORD, e.getMessage());
        }
        var account = ChildAccount.create(request.loginId(), passwordEncoder.encode(password.value()), guardianId,
            request.childProfileId());
        return ChildAccountResponse.from(accounts.saveAccount(account));
    }
}
