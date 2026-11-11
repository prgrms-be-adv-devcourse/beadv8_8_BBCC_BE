package com.bbcc.kidly.boundedcontext.recommend.in.referencegarment.dto;

import com.bbcc.kidly.boundedcontext.recommend.app.referencegarment.dto.PurchasedGarmentResult;
import java.time.LocalDateTime;

/**
 * 자녀가 구매확정한 옷 하나다. ID는 다른 응답처럼 문자열로 보낸다.
 *
 * @param orderItemId 구매확정한 주문상품의 ID
 * @param size 산 옷의 사이즈
 */
public record PurchasedGarmentResponse(String orderItemId, String productId, String size, LocalDateTime confirmedAt) {

    public static PurchasedGarmentResponse from(PurchasedGarmentResult result) {
        return new PurchasedGarmentResponse(
            String.valueOf(result.orderItemId()), String.valueOf(result.productId()), result.size(),
            result.confirmedAt());
    }
}
