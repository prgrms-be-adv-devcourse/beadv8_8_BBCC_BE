package com.bbcc.kidly.boundedcontext.order.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 주문이 받아 둔 상품 · 판매자 · 자녀 복사본 조회(#617). 장바구니가 쓴다. 구현은 주문 app이고 부르는 쪽 트랜잭션에 합류한다.
 */
public interface ReplicaApi {

    Optional<ProductOptionView> findProductOption(Long optionId);

    /**
     * 없는 옵션은 빠진다.
     */
    List<ProductOptionView> findProductOptions(Collection<Long> optionIds);

    Optional<String> findSellerName(Long sellerId);

    /**
     * 판매자 ID → 상호. 없는 판매자는 빠진다.
     */
    Map<Long, String> findSellerNames(Collection<Long> sellerIds);

    Optional<ChildView> findChild(Long childId);
}
