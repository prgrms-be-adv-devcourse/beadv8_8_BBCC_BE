package com.bbcc.kidly.boundedcontext.wish.app.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 구매 요청 한 건(#999). 상품 표시 · 가격 · 판매 상태는 지금 값이고, 수량은 보낸 때 칸 수량이다.
 *
 * @param type 요청 종류(WISH)
 * @param childId 받는 사람(자녀 프로필 ID)
 * @param status REQUESTED · PAID · REJECTED · CANCELLED
 * @param requestedByType 보낸 사람 종류(MEMBER · CHILD)
 * @param requestedById 보낸 사람 ID(회원 ID 또는 자녀 프로필 ID)
 * @param message 메시지. 없으면 null
 * @param payerType 결제한 사람 종류. 결제 완료가 아니면 null
 * @param payerId 결제한 사람 ID. 결제 완료가 아니면 null
 * @param paidAt 결제 완료 시각. 결제 완료가 아니면 null
 * @param rejectReason 거절 사유(TOO_EXPENSIVE · ALREADY_HAVE · NEXT_TIME · OTHER). 거절이 아니면 null
 * @param rejectedAt 거절 시각. 거절이 아니면 null
 * @param cancelReason 철회 원인(DIRECT · CHILD_DELETED · ACCOUNT_DELETED · SYSTEM). 철회가 아니면 null
 * @param cancelledAt 철회 시각. 철회가 아니면 null
 * @param purchases 조르기로 산 것(결제돼 장바구니에서 빠진 칸, 먼저 산 것부터)
 * @param paidOrderItemIds 조르기로 결제된 주문상품 ID(#1000, 먼저 산 것부터). 화면이 자녀 구매 내역과 맞춰 출처를 표시한다.
 *     장바구니 구매 이벤트에 주문상품 ID가 없던 산 것은 빠진다
 */
public record WishRequestView(
    Long requestId,
    String type,
    Long childId,
    String status,
    String requestedByType,
    Long requestedById,
    String message,
    LocalDateTime requestedAt,
    String payerType,
    Long payerId,
    LocalDateTime paidAt,
    String rejectReason,
    LocalDateTime rejectedAt,
    String cancelReason,
    LocalDateTime cancelledAt,
    List<Line> items,
    List<Purchase> purchases,
    List<Long> paidOrderItemIds) {

    /**
     * 조르기에 든 칸 하나. 상품 복사본이 없으면 상품 필드 null, purchasable false.
     *
     * @param cartItemId 장바구니 칸 ID. #999 전 조르기는 null
     * @param quantity 보낸 때 칸 수량
     * @param unitPrice 지금 가격
     * @param saleStatus 지금 판매 상태 값(ON_SALE · SOLD_OUT · STOPPED 등)
     * @param purchased 이 칸이 결제돼 장바구니에서 빠졌는지
     */
    public record Line(
        Long cartItemId,
        Long optionId,
        Long productId,
        Long sellerId,
        String productName,
        String optionName,
        String size,
        String sellerName,
        int quantity,
        Long unitPrice,
        boolean purchasable,
        String saleStatus,
        boolean purchased) {
    }

    /**
     * @param quantity 그 칸에서 이번에 산 수량(cart.item-purchased quantity, 칸에 남은 수량 아님)
     */
    public record Purchase(Long orderId, Long cartItemId, int quantity, LocalDateTime purchasedAt) {
    }
}
