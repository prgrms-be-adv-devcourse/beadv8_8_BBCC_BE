package com.bbcc.kidly.boundedcontext.review.out.persistence;

import com.bbcc.kidly.boundedcontext.review.app.port.ReplicaProducts;
import com.bbcc.kidly.boundedcontext.review.domain.ReplicaProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaReplicaProductRepository extends JpaRepository<ReplicaProduct, Long>, ReplicaProducts {
}
