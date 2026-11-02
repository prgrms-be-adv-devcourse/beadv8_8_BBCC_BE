package com.bbcc.kidly.boundedcontext.wish.app.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 자녀 한 명의 공용 장바구니(#640). 최근에 담은 줄부터.
 *
 * @param totalAmount 구매 가능한 줄의 금액 합
 */
public record WishView(Long childId, List<Line> items, long totalAmount) {

    /**
     * 한 줄. 가격 · 구매 가능 여부는 상품 복사본의 지금 값이다(장바구니와 같은 기준).
     * 상품 복사본이 없는 줄은 상품 필드가 null, purchasable false, lineAmount 0이다.
     *
     * @param sellerName 판매자의 지금 상호명(판매자 복사본). 상품 · 판매자 복사본이 없으면 null
     * @param addedBy 담은 사람 종류(PARENT · CHILD)
     * @param addedAt 처음 담은 시각
     * @param saleStatus 상품의 지금 판매 상태 값(ON_SALE · SOLD_OUT · STOPPED 등, #701). 상품 복사본이 없으면 null
     * @param requestId 이 줄이 든 진행 중인 조르기 ID(#701). 없으면 null. 있으면 다른 조르기에 넣거나 수량을 바꾸거나 뺄 수 없다
     */
    public record Line(
        Long wishItemId,
        Long childId,
        Long optionId,
        Long productId,
        Long sellerId,
        String productName,
        String optionName,
        String size,
        int quantity,
        Long unitPrice,
        boolean purchasable,
        long lineAmount,
        String sellerName,
        String addedBy,
        LocalDateTime addedAt,
        String saleStatus,
        Long requestId) {
    }
}
