package com.bbcc.kidly.boundedcontext.product.app.product.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 판매자가 상품의 판매 상태를 바꾸는 요청이다. 판매 시작(ON_SALE)과 판매 중지(STOPPED)만 받는다.
 * 판매대기로 되돌리는 요청, 정해지지 않은 값을 모두 같은 400으로 거절하려고 글자로 받는다.
 *
 * @param status 바꿀 판매 상태
 */
public record ProductSaleStatusRequest(
    @NotNull(message = SALE_START_OR_STOP_ONLY)
    @Pattern(regexp = "ON_SALE|STOPPED", message = SALE_START_OR_STOP_ONLY) String status) {

    private static final String SALE_START_OR_STOP_ONLY = "판매 시작(ON_SALE)이나 판매 중지(STOPPED)만 요청할 수 있습니다.";
}
