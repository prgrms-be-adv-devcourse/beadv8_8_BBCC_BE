package com.bbcc.kidly.boundedcontext.order.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/**
 * POST /orders. 장바구니를 읽지 않고 요청 값이 기준이다. unitPrice는 화면이 보여 준 단가(현재 단가와 다르면 409).
 * JSON의 문자열 ID("123")도 Long으로 받는다. 숫자가 아니거나 범위를 넘으면 400.
 */
public record PlaceOrderRequest(
    @NotEmpty List<@Valid @NotNull Item> items,
    @NotNull @Valid ShippingAddress shippingAddress) {

    /**
     * @param childId 자녀를 고르지 않으면 null
     */
    public record Item(
        @NotNull Long optionId,
        Long childId,
        @NotNull @Min(1) @Max(99) Integer quantity,
        @NotNull @PositiveOrZero Long unitPrice) {
    }

    public record ShippingAddress(
        @NotBlank String recipientName,
        @NotBlank String phone,
        @NotBlank String zipCode,
        @NotBlank String address) {
    }
}
