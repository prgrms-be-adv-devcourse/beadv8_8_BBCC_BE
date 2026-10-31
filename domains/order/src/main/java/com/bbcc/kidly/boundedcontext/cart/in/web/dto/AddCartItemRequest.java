package com.bbcc.kidly.boundedcontext.cart.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * POST /cart/items. JSON의 문자열 ID("123")도 Long으로 받는다. 숫자가 아니거나 범위를 넘으면 400.
 *
 * @param childId 자녀를 고르지 않으면 null
 */
public record AddCartItemRequest(
    @NotNull Long optionId,
    Long childId,
    @Min(1) @Max(99) int quantity) {
}
