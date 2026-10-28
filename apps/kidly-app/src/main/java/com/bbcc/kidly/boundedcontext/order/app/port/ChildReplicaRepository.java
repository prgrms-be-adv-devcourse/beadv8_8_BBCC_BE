package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import java.util.Optional;

/**
 * 자녀 복사본 저장소. 구현은 out/persistence (Spring Data가 만든다).
 */
public interface ChildReplicaRepository {

    ReplicaChild save(ReplicaChild replicaChild);

    Optional<ReplicaChild> findByChildId(Long childId);
}
