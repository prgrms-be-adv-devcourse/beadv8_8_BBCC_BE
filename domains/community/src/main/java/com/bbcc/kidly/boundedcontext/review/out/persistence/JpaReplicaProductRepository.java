package com.bbcc.kidly.boundedcontext.review.out.persistence;

import com.bbcc.kidly.boundedcontext.review.app.port.ReplicaProducts;
import com.bbcc.kidly.boundedcontext.review.domain.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.review.out.store.ReplicaProductInsert;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 상품 복사본 저장소. 새 행 저장(insert)은 persist만 하는 조각(out.store.ReplicaProductInsert)이 맡는다.
 */
public interface JpaReplicaProductRepository
    extends JpaRepository<ReplicaProduct, Long>, ReplicaProducts, ReplicaProductInsert {
}
