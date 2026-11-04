package com.bbcc.kidly.boundedcontext.auth.app;

import com.bbcc.kidly.boundedcontext.auth.app.dto.ChildAccountCreateRequest;
import com.bbcc.kidly.boundedcontext.auth.app.dto.ChildAccountResponse;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthErrorCode;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccount;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccountRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildPassword;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildProfileReplicaRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.RefreshTokenRepository;
import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.shared.auth.PrincipalId;
import java.time.LocalDateTime;
import java.util.List;
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
    private final RefreshTokenRepository refreshTokens;
    private final AccessTokenRevocationService revocationService;

    @Transactional(readOnly = true)
    public List<ChildAccountResponse> list(Long guardianId) {
        return accounts.findAllByGuardianIdOrderByIdAsc(guardianId).stream().map(ChildAccountResponse::from).toList();
    }

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
        var password = validatePassword(request.password());
        var account = ChildAccount.create(request.loginId(), passwordEncoder.encode(password.value()), guardianId,
            request.childProfileId());
        return ChildAccountResponse.from(accounts.saveAccount(account));
    }

    @Transactional
    public void resetPassword(Long guardianId, Long accountId, String value) {
        var account = ownedAccount(guardianId, accountId);
        var password = validatePassword(value);
        account.changePassword(passwordEncoder.encode(password.value()));
        revokeTokens(account.getId());
    }

    private ChildPassword validatePassword(String value) {
        try {
            return new ChildPassword(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(AuthErrorCode.INVALID_CHILD_PASSWORD, e.getMessage());
        }
    }

    @Transactional
    public void delete(Long guardianId, Long accountId) {
        deleteAccount(ownedAccount(guardianId, accountId));
    }

    void deleteAccount(ChildAccount account) {
        accounts.delete(account);
        revokeTokens(account.getId());
    }

    private ChildAccount ownedAccount(Long guardianId, Long accountId) {
        return accounts.findByIdForUpdate(accountId).filter(a -> a.getGuardianId().equals(guardianId))
            .orElseThrow(() -> new BusinessException(AuthErrorCode.CHILD_ACCOUNT_NOT_FOUND));
    }

    private void revokeTokens(Long accountId) {
        var principal = PrincipalId.child(accountId);
        refreshTokens.deleteByPrincipal(principal);
        revocationService.revoke(principal, LocalDateTime.now());
    }
}
