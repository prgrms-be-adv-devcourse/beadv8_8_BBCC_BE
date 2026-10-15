package com.bbcc.kidly.boundedcontext.product.app.inventory.dto;

/**
 * 판매자가 옵션 하나의 재고를 증감량과 사유로 조정하는 요청이다.
 * 재고를 열 때만 쓰는 INITIAL이나 정해지지 않은 값을 모두 같은 400으로 거절하려고 사유를 글자로 받는다.
 *
 * @param deltaQuantity 늘리거나 줄일 수량. 늘리면 양수, 줄이면 음수다
 * @param reason 조정 사유
 */
public record InventoryAdjustmentRequest(Integer deltaQuantity, String reason) {
}
