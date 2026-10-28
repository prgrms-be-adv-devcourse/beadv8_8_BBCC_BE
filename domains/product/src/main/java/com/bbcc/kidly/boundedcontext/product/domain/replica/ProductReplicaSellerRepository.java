package com.bbcc.kidly.boundedcontext.product.domain.replica;

import java.util.Optional;

public interface ProductReplicaSellerRepository {

    Optional<ProductReplicaSeller> findById(Long sellerId);

    ProductReplicaSeller save(ProductReplicaSeller replicaSeller);
}
