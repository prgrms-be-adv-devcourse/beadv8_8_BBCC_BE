package com.bbcc.kidly.boundedcontext.product.app.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 증감량이 0이거나 사유와 부호가 맞지 않는 요청은 클래스 단위 검증(InventoryAdjustmentSign)이 거절한다.
 *
 * @param deltaQuantity 늘리거나 줄일 수량. 늘리면 양수, 줄이면 음수다
 */
@InventoryAdjustmentSign
public record InventoryAdjustmentRequest(
    @NotNull(message = "증감량을 넣어 주세요.")
    @Min(value = -1_000_000, message = RANGE)
    @Max(value = 1_000_000, message = RANGE)
    Integer deltaQuantity,
    @NotNull(message = "사유를 넣어 주세요.")
    @Pattern(regexp = "RESTOCK|DEFECTIVE|LOST|CORRECTION", message = "재고 조정에 쓸 수 없는 사유입니다: ${validatedValue}")
    String reason) {

    private static final String RANGE = "증감량은 -1000000 이상 1000000 이하여야 합니다.";
}
