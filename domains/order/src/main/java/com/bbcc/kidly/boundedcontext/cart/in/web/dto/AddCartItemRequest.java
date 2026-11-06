package com.bbcc.kidly.boundedcontext.cart.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * POST /cart/items. JSON의 문자열 ID("123")도 Long으로 받는다. 숫자가 아니거나 범위를 넘으면 400.
 *
 * @param childId 입을 자녀. 회원은 고르지 않으면 null, 로그인한 자녀는 비우거나 자기 프로필(다르면 400, #998)
 */
public record AddCartItemRequest(
    @NotNull Long optionId,
    Long childId,
    @Min(1) @Max(99) int quantity) {
}
