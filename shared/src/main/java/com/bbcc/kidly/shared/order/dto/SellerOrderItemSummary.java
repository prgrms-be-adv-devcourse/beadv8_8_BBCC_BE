package com.bbcc.kidly.shared.order.dto;

/**
 * 판매자 상품의 주문상품 요약(#1120). 판매자 상태 · 탈퇴 같은 회원 개념은 넣지 않는다.
 *
 * @param sellerId 판매자 ID
 * @param openItemCount 미종결 주문상품 수: 주문이 취소되지 않았고 주문상품이 주문됨(ORDERED)인 수(결제대기 · 결제완료 · 배송 준비 ·
 *     배송 중 · 배송 완료 후 구매확정 전). 구매확정 · 취소된 주문상품과 취소된 주문의 주문상품은 빠진다
 */
public record SellerOrderItemSummary(Long sellerId, long openItemCount) {
}
