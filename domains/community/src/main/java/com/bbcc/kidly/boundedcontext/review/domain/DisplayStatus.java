package com.bbcc.kidly.boundedcontext.review.domain;

import java.util.List;

/**
 * 리뷰 · 찜 화면에 보이는 상품 표시 상태. 이후 API 응답 값이 된다.
 */
public enum DisplayStatus {
    ON_SALE,
    SOLD_OUT,
    SALE_ENDED;

    /**
     * 품절은 팔고 있지만 재고가 없는 것, 판매 종료는 더 이상 팔지 않는 것이다.
     * <ul>
     *   <li>판매 종료: 상품이 판매 중이 아니다, 또는 판매 중이지만 판매 중 옵션이 0개다</li>
     *   <li>품절: 상품이 판매 중이고 판매 중 옵션이 1개 이상이며 그 옵션에 모두 재고가 없다</li>
     *   <li>판매 중: 그 밖(판매 중 옵션 하나라도 재고가 있다)</li>
     * </ul>
     *
     * @param productOnSale 상품이 판매 중인지
     * @param options 옵션의 판매 중 여부 · 재고 유무. 판매 중이 아닌 옵션은 보지 않는다. null이면 옵션 없음
     */
    public static DisplayStatus of(boolean productOnSale, List<ReplicaProduct.OptionState> options) {
        if (!productOnSale || options == null) {
            return SALE_ENDED;
        }
        List<ReplicaProduct.OptionState> onSale = options.stream()
            .filter(ReplicaProduct.OptionState::onSale)
            .toList();
        if (onSale.isEmpty()) {
            return SALE_ENDED;
        }
        return onSale.stream().anyMatch(ReplicaProduct.OptionState::inStock) ? ON_SALE : SOLD_OUT;
    }
}
