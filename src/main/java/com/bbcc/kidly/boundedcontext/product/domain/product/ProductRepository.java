package com.bbcc.kidly.boundedcontext.product.domain.product;

import java.util.Optional;

public interface ProductRepository {

    // 상품을 저장한다. 옵션 · 실측 · 정보 값은 상품과 함께 저장된다
    Product save(Product product);

    // 상품을 ID로 찾는다. 없으면 빈 값을 돌려준다
    Optional<Product> findById(Long id);
}
