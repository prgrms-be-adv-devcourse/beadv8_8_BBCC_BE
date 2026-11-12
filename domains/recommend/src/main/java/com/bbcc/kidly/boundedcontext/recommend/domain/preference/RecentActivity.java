package com.bbcc.kidly.boundedcontext.recommend.domain.preference;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import java.time.LocalDateTime;

/**
 * 선호 계산이 읽는 활동 한 건이다. 관심을 거둔 활동(장바구니에 담았다가 뺀 담기, 직접 철회한 조르기)은 withdrawn이 true다.
 * 검색은 상품이 없어 productId와 brand가 null일 수 있다.
 */
public record RecentActivity(
        ActivityType type,
        Long productId,
        Long categoryId,
        String brand,
        LocalDateTime occurredAt,
        boolean withdrawn
) {
}
