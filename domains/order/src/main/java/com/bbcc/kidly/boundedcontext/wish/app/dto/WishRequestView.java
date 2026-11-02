package com.bbcc.kidly.boundedcontext.wish.app.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 조르기 한 건(#701). 상품 표시 · 가격 · 판매 상태는 지금 값이다(공용 장바구니와 같은 기준).
 *
 * @param status REQUESTED · APPROVED · REJECTED
 * @param requestedBy 보낸 사람 종류(PARENT · CHILD)
 * @param message 메시지. 없으면 null
 * @param checkout 승인 뒤 구매 방법(ORDER = 바로 결제 · CART = 보호자 장바구니로 옮기기). 승인 전 · 거절이면 null
 * @param orderId 바로 결제로 만든 주문 ID. 없으면 null
 * @param orderResult 바로 결제한 주문의 결과(PAID · CANCELLED). 정해지기 전이면 null
 * @param rejectReason 거절 사유(TOO_EXPENSIVE · ALREADY_HAVE · NEXT_TIME · OTHER). 거절이 아니면 null
 * @param decidedAt 승인 · 거절 시각. 처리 전이면 null
 */
public record WishRequestView(
    Long requestId,
    Long childId,
    String status,
    String requestedBy,
    String message,
    LocalDateTime requestedAt,
    String checkout,
    Long orderId,
    String orderResult,
    String rejectReason,
    LocalDateTime decidedAt,
    List<Line> items) {

    /**
     * 조르기에 든 줄 하나. 상품 복사본이 없으면 상품 필드 null, purchasable false.
     *
     * @param requestedQuantity 조른 수량
     * @param approvedQuantity 승인 수량. 승인하지 않은 줄 · 처리 전이면 null
     * @param unitPrice 지금 가격
     * @param saleStatus 지금 판매 상태 값(ON_SALE · SOLD_OUT · STOPPED 등)
     */
    public record Line(
        Long wishItemId,
        Long optionId,
        Long productId,
        Long sellerId,
        String productName,
        String optionName,
        String size,
        String sellerName,
        int requestedQuantity,
        Integer approvedQuantity,
        Long unitPrice,
        boolean purchasable,
        String saleStatus) {
    }
}
