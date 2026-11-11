package com.bbcc.kidly.boundedcontext.recommend.app.referencegarment.dto;

import com.bbcc.kidly.boundedcontext.recommend.app.port.purchase.PurchasedGarmentRow;
import java.time.LocalDateTime;

public record PurchasedGarmentResult(Long orderItemId, Long productId, String size, LocalDateTime confirmedAt) {

    public static PurchasedGarmentResult from(PurchasedGarmentRow row) {
        return new PurchasedGarmentResult(row.orderItemId(), row.productId(), row.size(), row.confirmedAt());
    }
}
