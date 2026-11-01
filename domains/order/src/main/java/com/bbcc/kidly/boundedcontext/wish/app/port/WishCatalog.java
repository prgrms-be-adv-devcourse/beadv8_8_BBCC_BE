package com.bbcc.kidly.boundedcontext.wish.app.port;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 조르기에 담을 상품 옵션 · 판매자 상호 · 자녀(#641). 구현은 out의 주문 컨텍스트(복사본) 어댑터이고 부르는 쪽 트랜잭션에 합류한다.
 */
public interface WishCatalog {

    Optional<ProductOption> findProductOption(Long optionId);

    /**
     * 없는 옵션은 빠진다.
     */
    List<ProductOption> findProductOptions(Collection<Long> optionIds);

    Optional<String> findSellerName(Long sellerId);

    /**
     * 판매자 ID → 상호. 없는 판매자는 빠진다.
     */
    Map<Long, String> findSellerNames(Collection<Long> sellerIds);

    Optional<Child> findChild(Long childId);

    /**
     * @param purchasable 지금 살 수 있는지(판매 중 · 옵션 판매 중 · 품절 아님). 장바구니와 같은 판단
     */
    record ProductOption(
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
     * @param memberId 보호자 회원 ID
     */
    record Child(Long childId, Long memberId, boolean deleted) {
    }
}
