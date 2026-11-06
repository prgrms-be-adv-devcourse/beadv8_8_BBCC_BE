package com.bbcc.kidly.boundedcontext.auth.app;

import com.bbcc.kidly.boundedcontext.auth.domain.ChildProfileReplicaRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChildProfileReplicaService {

    private final ChildProfileReplicaRepository repository;
    private final ChildAccountService childAccounts;

    @Transactional
    public void apply(Long childId, Long guardianId, boolean deleted) {
        if (deleted) {
            // 계정 만들기도 이 행을 잠근다. 먼저 잠그면 만들기가 404를 보고, 늦게 잠그면 아래 조회가 막 만든 계정을 본다
            repository.findByIdForUpdate(childId);
            repository.deleteById(childId);
            childAccounts.deleteByChildProfileId(childId);
        } else {
            repository.insertIfAbsent(childId, guardianId, LocalDateTime.now());
        }
    }
}
