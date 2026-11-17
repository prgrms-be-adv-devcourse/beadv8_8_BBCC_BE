package com.bbcc.kidly.boundedcontext.product.app.inventory.dto;

public record InventoryAdjustmentResult(long optionId, String reason, int deltaQuantity, int availableQuantity) {
}
