package com.bbcc.kidly.boundedcontext.notification.domain;

import java.util.Optional;

public interface ProductNameRepository {

    ProductName save(ProductName productName);

    Optional<ProductName> findById(Long productId);
}
