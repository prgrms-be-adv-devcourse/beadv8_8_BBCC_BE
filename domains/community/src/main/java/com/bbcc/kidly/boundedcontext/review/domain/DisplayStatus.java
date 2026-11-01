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
     * 판매 종료: 상품이 판매 중이 아니다(STOPPED · SALE_PENDING · 모르는 값 · 없음).
     * 품절: 상품이 판매 중이고 판매 중인 옵션이 모두 품절이다(판매 중 옵션이 하나도 없어도 품절). 판매 중: 그 밖.
     *
     * @param productStatus 상품 판매 상태 이름
     * @param options 옵션 판매 상태 · 품절 여부. 판매 중(ON_SALE)이 아닌 옵션은 세지 않는다
     */
    public static DisplayStatus of(String productStatus, List<ReplicaProduct.OptionState> options) {
        if (!SALE.equals(productStatus)) {
            return SALE_ENDED;
        }
        boolean anyInStock = options.stream()
            .anyMatch(option -> SALE.equals(option.saleStatus()) && !option.soldOut());
        return anyInStock ? ON_SALE : SOLD_OUT;
    }
}
