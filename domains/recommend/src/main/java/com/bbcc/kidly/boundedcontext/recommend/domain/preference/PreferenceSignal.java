package com.bbcc.kidly.boundedcontext.recommend.domain.preference;

import java.time.LocalDateTime;

/**
 * 선호 계산에 넣는 신호 하나다. 활동 기록이나 구매 기록 한 줄에서 만든다.
 * 카테고리 · 브랜드는 신호가 생긴 때의 값이고, withdrawn은 관심을 거둔 활동(장바구니에서 뺀 담기, 직접 철회한 조르기)인지를 나타낸다.
 */
public record PreferenceSignal(
        PreferenceSignalType type,
        LocalDateTime occurredAt,
        Long categoryId,
        String brand,
        Long productId,
        boolean withdrawn
) {
}
