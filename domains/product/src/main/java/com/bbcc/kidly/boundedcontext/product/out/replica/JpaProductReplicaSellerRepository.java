package com.bbcc.kidly.boundedcontext.product.out.replica;

import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSeller;
import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSellerRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProductReplicaSellerRepository
    extends JpaRepository<ProductReplicaSeller, Long>, ProductReplicaSellerRepository {
}
