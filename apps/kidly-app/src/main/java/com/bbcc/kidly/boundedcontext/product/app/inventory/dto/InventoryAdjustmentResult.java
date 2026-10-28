package com.bbcc.kidly.boundedcontext.product.app.inventory.dto;

/**
 * 재고를 조정한 뒤의 결과다. in 계층이 domain을 참조하지 않도록 사유는 이름 글자로 담는다.
 */
public record InventoryAdjustmentResult(long optionId, String reason, int deltaQuantity, int availableQuantity) {
}
