package com.bbcc.kidly.boundedcontext.wish.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * POST /wishes(#640). JSON의 문자열 ID("123")도 Long으로 받는다. 숫자가 아니거나 범위를 넘으면 400.
 *
 * @param childId 공용 장바구니의 자녀(프로필 ID)
 */
public record AddWishItemRequest(
    @NotNull Long childId,
    @NotNull Long optionId,
    @Min(1) @Max(99) int quantity) {
}
