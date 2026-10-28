package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import org.springframework.data.repository.Repository;

/**
 * 상품 복사본 저장소를 Spring Data JPA로 구현한다. 도메인 인터페이스에 선언한 메서드만 노출한다.
 */
public interface JpaProductReplicaRepository extends Repository<ReplicaProduct, Long>, ProductReplicaRepository {
}
