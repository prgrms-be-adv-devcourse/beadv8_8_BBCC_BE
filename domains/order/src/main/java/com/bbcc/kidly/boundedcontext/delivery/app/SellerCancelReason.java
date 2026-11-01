package com.bbcc.kidly.boundedcontext.delivery.app;

/**
 * 판매자 발송 전 취소 사유(#655). 요청 값 확인용이고, 이름 그대로 주문(주문상품 취소 사유)에 넘긴다.
 */
public enum SellerCancelReason {
    OUT_OF_STOCK,
    WRONG_PRODUCT_INFO,
    OTHER
}
