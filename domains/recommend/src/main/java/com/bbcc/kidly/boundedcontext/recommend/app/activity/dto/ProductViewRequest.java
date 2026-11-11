package com.bbcc.kidly.boundedcontext.recommend.app.activity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * JSON의 문자열 ID("35")도 Long으로 받는다.
 * childId는 화면에서 자녀를 고른 경우만 보내고, 없으면 회원의 활동으로 기록한다.
 */
public record ProductViewRequest(
    @NotNull(message = "상품 ID가 필요합니다.") @Positive(message = "상품 ID는 1 이상이어야 합니다.") Long productId,
    @Positive(message = "자녀 ID는 1 이상이어야 합니다.") Long childId) {
}
