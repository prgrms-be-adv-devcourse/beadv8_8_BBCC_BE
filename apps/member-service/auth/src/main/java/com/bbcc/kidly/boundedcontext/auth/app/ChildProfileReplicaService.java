package com.bbcc.kidly.boundedcontext.auth.app;

import com.bbcc.kidly.boundedcontext.auth.domain.ChildProfileReplicaRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccountRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.RefreshTokenRepository;
import com.bbcc.kidly.shared.auth.PrincipalId;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChildProfileReplicaService {

    private final ChildProfileReplicaRepository repository;
    private final ChildAccountRepository accounts;
    private final AccessTokenRevocationService revocationService;
    private final RefreshTokenRepository refreshTokens;

    @Transactional
    public void apply(Long childId, Long guardianId, boolean deleted) {
        if (deleted) {
            // 계정 만들기도 이 행을 잠근다. 먼저 잠그면 만들기가 404를 보고, 늦게 잠그면 아래 조회가 막 만든 계정을 본다
            repository.findByIdForUpdate(childId);
            repository.deleteById(childId);
            accounts.findByChildProfileId(childId).ifPresent(account -> {
                accounts.delete(account);
                var principal = PrincipalId.child(account.getId());
                refreshTokens.deleteByPrincipal(principal);
                revocationService.revoke(principal, LocalDateTime.now());
            });
        } else {
            repository.insertIfAbsent(childId, guardianId, LocalDateTime.now());
        }
    }
}
