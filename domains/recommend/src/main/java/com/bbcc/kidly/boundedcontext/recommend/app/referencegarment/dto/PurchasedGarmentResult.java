package com.bbcc.kidly.boundedcontext.recommend.app.referencegarment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 자녀가 구매확정한 옷 하나다. 상품 복사본이 없거나 산 사이즈의 실측이 없으면 해당 칸은 null이다.
 * 기준 옷 칸 이름(part)은 상품 복사본이 없거나 카테고리가 어느 칸에도 들지 않으면 null이다.
 */
public record PurchasedGarmentResult(Long orderItemId, Long productId, String productName, Long categoryId,
    String part, String size, Map<String, BigDecimal> measurements, LocalDateTime confirmedAt) {
}
