package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import org.springframework.data.repository.Repository;

/**
 * 자녀 복사본 저장소를 Spring Data JPA로 구현한다. 도메인 인터페이스에 선언한 메서드만 노출한다.
 */
public interface JpaChildReplicaRepository extends Repository<ReplicaChild, Long>, ChildReplicaRepository {
}
