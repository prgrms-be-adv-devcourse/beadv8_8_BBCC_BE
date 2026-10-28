package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.SellerReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaSeller;
import org.springframework.data.repository.Repository;

/**
 * 판매자 복사본 저장소를 Spring Data JPA로 구현한다. 도메인 인터페이스에 선언한 메서드만 노출한다.
 */
public interface JpaSellerReplicaRepository extends Repository<ReplicaSeller, Long>, SellerReplicaRepository {
}
