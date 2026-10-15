package com.bbcc.kidly.boundedcontext.product.domain.product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    // 상품을 저장한다. 옵션 · 실측 · 정보 값은 상품과 함께 저장된다
    Product save(Product product);

    // 저장하고 바로 DB에 반영해, 늘어난 version을 같은 트랜잭션에서 읽을 수 있게 한다
    Product saveAndFlush(Product product);

    // 상품을 ID로 찾는다. 없으면 빈 값을 돌려준다
    Optional<Product> findById(Long id);

    // 재고를 차감할 옵션을 ID 목록으로 상품과 함께 찾는다. 없는 ID는 결과에서 빠진다.
    // 호출한 쪽에 바깥 트랜잭션이 없으면 조회 직후 옵션이 준영속이 되므로, 판매 가능 여부를 판단할 수 있게 상품을 함께 읽어 둔다.
    // 옵션마다 상품을 따로 조회하는 쿼리가 나가는 것도 막는다
    List<ProductOption> findOptionsWithProduct(Collection<Long> optionIds);

    // 판매자의 옵션이 이미 쓰는 SKU 코드 중 skuCodes에 든 것을 돌려준다.
    // 옵션의 판매 상태(판매중 · 판매중지 · 보관)와 상관없이 찾는다. skuCodes가 비면 빈 목록을 돌려준다
    List<String> findSkuCodesInUse(Long sellerId, Collection<String> skuCodes);
}
