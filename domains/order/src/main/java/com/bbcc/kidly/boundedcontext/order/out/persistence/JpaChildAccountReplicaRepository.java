package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.ChildAccountReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChildAccount;
import org.springframework.data.repository.Repository;

/**
 * 자녀 계정 복사본 저장소를 Spring Data JPA로 구현한다(#906). 도메인 인터페이스에 선언한 메서드만 노출한다.
 */
public interface JpaChildAccountReplicaRepository extends Repository<ReplicaChildAccount, Long>,
    ChildAccountReplicaRepository {
}
