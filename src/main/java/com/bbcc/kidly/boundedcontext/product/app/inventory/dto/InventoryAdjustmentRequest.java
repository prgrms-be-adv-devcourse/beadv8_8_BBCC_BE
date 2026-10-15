package com.bbcc.kidly.boundedcontext.product.app.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 판매자가 옵션 하나의 재고를 증감량과 사유로 조정하는 요청이다.
 * 재고를 열 때만 쓰는 INITIAL이나 정해지지 않은 값을 모두 같은 400으로 거절하려고 사유를 글자로 받는다.
 * 증감량이 0이거나 사유와 부호가 맞지 않는 요청은 클래스 단위 검증(InventoryAdjustmentSign)이 거절한다.
 *
 * @param deltaQuantity 늘리거나 줄일 수량. 늘리면 양수, 줄이면 음수다
 * @param reason 조정 사유
 */
@InventoryAdjustmentSign
public record InventoryAdjustmentRequest(
    // 범위를 두는 이유: 판매 가능 수량에 더한 값이 정수 범위를 넘으면 DB 오류(500)가 나기 때문이다
    @NotNull(message = "증감량을 넣어 주세요.")
    @Min(value = -1_000_000, message = RANGE)
    @Max(value = 1_000_000, message = RANGE)
    Integer deltaQuantity,
    // INITIAL은 옵션을 등록할 때 서버만 남기는 사유라 판매자 요청으로 받지 않는다
    @NotNull(message = "사유를 넣어 주세요.")
    @Pattern(regexp = "RESTOCK|DEFECTIVE|LOST|CORRECTION", message = "재고 조정에 쓸 수 없는 사유입니다: ${validatedValue}")
    String reason) {

    private static final String RANGE = "증감량은 -1000000 이상 1000000 이하여야 합니다.";
}
