package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import java.util.List;
import java.util.Optional;

/**
 * 자녀 복사본 저장소. 구현은 out/persistence (Spring Data가 만든다).
 */
public interface ChildReplicaRepository {

    ReplicaChild save(ReplicaChild replicaChild);

    Optional<ReplicaChild> findByChildId(Long childId);

    /**
     * 그 회원이 보호자인 자녀 복사본(삭제된 자녀 포함).
     */
    List<ReplicaChild> findAllByMemberId(Long memberId);
}
