package com.bbcc.kidly.boundedcontext.recommend.app.activity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * JSON의 문자열 ID("35")도 Long으로 받는다.
 */
public record ProductViewRequest(
    @NotNull(message = "상품 ID가 필요합니다.") @Positive(message = "상품 ID는 1 이상이어야 합니다.") Long productId) {
}
