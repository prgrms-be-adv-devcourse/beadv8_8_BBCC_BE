package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChildAccount;
import java.util.Optional;

/**
 * 자녀 계정 복사본 저장소(#906). 구현은 out/persistence (Spring Data가 만든다).
 */
public interface ChildAccountReplicaRepository {

    ReplicaChildAccount save(ReplicaChildAccount replicaChildAccount);

    Optional<ReplicaChildAccount> findByAccountId(Long accountId);
}
