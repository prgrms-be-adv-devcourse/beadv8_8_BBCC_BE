package com.bbcc.kidly.boundedcontext.product.in.inventory.dto;

import com.bbcc.kidly.boundedcontext.product.app.inventory.dto.InventoryAdjustmentResult;

public record InventoryAdjustmentResponse(String optionId, String reason, int deltaQuantity, int availableQuantity) {

    public static InventoryAdjustmentResponse from(InventoryAdjustmentResult result) {
        return new InventoryAdjustmentResponse(
            String.valueOf(result.optionId()), result.reason(), result.deltaQuantity(), result.availableQuantity());
    }
}
