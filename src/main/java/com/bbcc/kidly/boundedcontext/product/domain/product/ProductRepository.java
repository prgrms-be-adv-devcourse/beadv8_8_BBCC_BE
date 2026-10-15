package com.bbcc.kidly.boundedcontext.product.domain.product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    // 상품을 저장한다. 옵션 · 실측 · 정보 값은 상품과 함께 저장된다
    Product save(Product product);

    // 상품을 ID로 찾는다. 없으면 빈 값을 돌려준다
    Optional<Product> findById(Long id);

    // 재고를 차감할 옵션을 ID 목록으로 상품과 함께 찾는다. 없는 ID는 결과에서 빠진다.
    // 차감이 영속성 컨텍스트를 비운 뒤에도 옵션의 판매 가능 여부를 판단할 수 있다
    List<ProductOption> findOptionsWithProduct(Collection<Long> optionIds);
}
