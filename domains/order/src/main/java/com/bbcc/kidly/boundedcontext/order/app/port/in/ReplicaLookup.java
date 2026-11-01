package com.bbcc.kidly.boundedcontext.order.app.port.in;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 주문이 받아 둔 상품 · 판매자 · 자녀 복사본 조회(#617 · #641). 장바구니 · 조르기가 쓴다. 구현은 주문 app이고 부르는 쪽 트랜잭션에 합류한다.
 */
public interface ReplicaLookup {

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

    /**
     * 상품 옵션 복사본.
     *
     * @param purchasable 지금 살 수 있는지(판매 중 · 옵션 판매 중 · 품절 아님). 주문 복사본이 정한 값 그대로
     */
    record ProductOptionView(
        Long optionId,
        Long productId,
        Long sellerId,
        String productName,
        String optionName,
        String size,
        long unitPrice,
        boolean purchasable) {
    }

    /**
     * 자녀 복사본.
     *
     * @param memberId 보호자 회원 ID
     */
    record ChildView(Long childId, Long memberId, boolean deleted) {
    }
}
