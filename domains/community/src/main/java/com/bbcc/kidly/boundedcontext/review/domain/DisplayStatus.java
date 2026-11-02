package com.bbcc.kidly.boundedcontext.review.domain;

import java.util.List;

/**
 * 리뷰 · 찜 화면에 보이는 상품 표시 상태. 이후 API 응답 값이 된다.
 */
public enum DisplayStatus {
    ON_SALE,
    SOLD_OUT,
    SALE_ENDED;

    private static final String SALE = "ON_SALE";

    /**
     * 품절은 팔고 있지만 재고가 없는 것, 판매 종료는 더 이상 팔지 않는 것이다.
     * <ul>
     *   <li>판매 종료: 상품이 판매 중이 아니다(STOPPED · SALE_PENDING · 모르는 값 · 없음), 또는 판매 중이지만 판매 중 옵션이 0개다</li>
     *   <li>품절: 상품이 판매 중이고 판매 중 옵션이 1개 이상이며 그 옵션이 모두 품절이다</li>
     *   <li>판매 중: 그 밖(판매 중 옵션 하나라도 재고가 있다)</li>
     * </ul>
     *
     * @param productStatus 상품 판매 상태 이름
     * @param options 옵션 판매 상태 · 품절 여부. 판매 중(ON_SALE)이 아닌 옵션(판매 중지 · 모르는 값)은 보지 않는다. null이면 옵션 없음
     */
    public static DisplayStatus of(String productStatus, List<ReplicaProduct.OptionState> options) {
        if (!SALE.equals(productStatus) || options == null) {
            return SALE_ENDED;
        }
        List<ReplicaProduct.OptionState> onSale = options.stream()
            .filter(option -> SALE.equals(option.saleStatus()))
            .toList();
        if (onSale.isEmpty()) {
            return SALE_ENDED;
        }
        return onSale.stream().allMatch(ReplicaProduct.OptionState::soldOut) ? SOLD_OUT : ON_SALE;
    }
}
