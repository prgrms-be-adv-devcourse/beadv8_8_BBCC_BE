package com.bbcc.kidly.boundedcontext.recommend.in.referencegarment.dto;

import com.bbcc.kidly.boundedcontext.recommend.app.referencegarment.dto.PurchasedGarmentResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 자녀가 구매확정한 옷 하나다. ID는 다른 응답처럼 문자열로 보낸다.
 *
 * @param orderItemId 구매확정한 주문상품의 ID
 * @param categoryId 상품 말단 카테고리의 ID
 * @param part 기준 옷 칸 이름(TOP · BOTTOM · OUTER · DRESS · SKIRT). 상품 복사본이 없거나 카테고리가 어느 칸에도 들지 않으면 null
 * @param size 산 옷의 사이즈
 * @param measurements 산 사이즈의 실측 항목 코드별 cm
 */
public record PurchasedGarmentResponse(String orderItemId, String productId, String productName, String categoryId,
    String part, String size, Map<String, BigDecimal> measurements, LocalDateTime confirmedAt) {

    public static PurchasedGarmentResponse from(PurchasedGarmentResult result) {
        return new PurchasedGarmentResponse(
            String.valueOf(result.orderItemId()), String.valueOf(result.productId()), result.productName(),
            result.categoryId() == null ? null : String.valueOf(result.categoryId()),
            result.part(), result.size(), result.measurements(), result.confirmedAt());
    }
}
