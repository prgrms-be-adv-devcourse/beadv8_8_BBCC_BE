package com.bbcc.kidly.boundedcontext.order.in.web.admin.dto;

import com.bbcc.kidly.boundedcontext.order.app.order.PurchaseConfirmationRepublishService.Result;

public record PurchaseConfirmationReplayResponse(int published, String throughOrderItemId,
    String nextAfterOrderItemId, boolean hasNext) {

    public static PurchaseConfirmationReplayResponse from(Result result) {
        return new PurchaseConfirmationReplayResponse(result.published(), Long.toString(result.throughOrderItemId()),
            result.nextAfterOrderItemId() == null ? null : result.nextAfterOrderItemId().toString(), result.hasNext());
    }
}
