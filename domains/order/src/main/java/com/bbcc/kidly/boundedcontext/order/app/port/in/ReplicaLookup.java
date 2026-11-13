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
     * 그 회원이 보호자인 자녀 복사본(삭제된 자녀 포함, #999 조르기 승인자 목록). 없으면 빈 목록.
     */
    List<ChildView> findChildrenOfMember(Long memberId);

    /**
     * 자녀 계정 → 자녀 프로필 · 보호자 복사본(#906, child-account.changed). 받은 적 없는 계정은 빈 값.
     */
    Optional<ChildAccountView> findChildAccount(Long accountId);

    /**
     * 상품 옵션 복사본.
     *
     * @param purchasable 지금 살 수 있는지(판매 중 · 옵션 판매 중 · 품절 아님). 주문 복사본이 정한 값 그대로
     * @param saleStatus 판매 상태 값(ON_SALE · SOLD_OUT · STOPPED · SALE_PENDING 등, #701). ON_SALE일 때만 purchasable
     * @param thumbnailUrl 상품 썸네일 URL(#1328). 없으면 null
     */
    record ProductOptionView(
        Long optionId,
        Long productId,
        Long sellerId,
        String productName,
        String optionName,
        String size,
        long unitPrice,
        boolean purchasable,
        String saleStatus,
        String thumbnailUrl) {
    }

    /**
     * 자녀 복사본.
     *
     * @param memberId 보호자 회원 ID
     */
    record ChildView(Long childId, Long memberId, boolean deleted) {
    }

    /**
     * 자녀 계정 복사본.
     *
     * @param guardianId 보호자 회원 ID
     * @param deleted 계정이 지워졌으면 true(한 번 지워지면 되돌지 않는다)
     */
    record ChildAccountView(Long accountId, Long childProfileId, Long guardianId, boolean deleted) {
    }
}
