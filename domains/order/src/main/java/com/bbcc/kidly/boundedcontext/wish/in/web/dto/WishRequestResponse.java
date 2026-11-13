package com.bbcc.kidly.boundedcontext.wish.in.web.dto;

import com.bbcc.kidly.boundedcontext.wish.app.dto.WishRequestView;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 구매 요청(조르기) 한 건(#999). ID는 문자열(장바구니와 같음). 가격 · 판매 상태는 지금 값이다. 칸은 추가만 한다.
 *
 * @param type 요청 종류(WISH)
 * @param childId 받는 사람(자녀 프로필 ID)
 * @param status REQUESTED · PAID · REJECTED · CANCELLED · EXPIRED(#1008)
 * @param requestedByType 보낸 사람 종류(MEMBER · CHILD)
 * @param payerType 결제한 사람 종류. 결제 완료가 아니면 null
 * @param rejectReason 거절 사유. 거절이 아니면 null
 * @param cancelReason 철회 원인(DIRECT · CHILD_DELETED · ACCOUNT_DELETED · SYSTEM). 철회가 아니면 null
 * @param purchases 조르기로 산 것(결제돼 장바구니에서 빠진 칸)
 * @param paidOrderItemIds 조르기로 결제된 주문상품 ID(#1000). 화면이 자녀 구매 내역(GET /api/v1/orders/items/mine)의
 *     orderItemId와 맞춰 "조르기로 산 것"을 표시한다. 없으면 빈 목록
 * @param expiresAt 만료 기한(보낸 시각 + 지금 설정 기한, #1008). 대기 중이고 만료가 켜져 있을 때만, 그 밖 null
 * @param expiredAt 만료 처리 시각(#1008). 만료가 아니면 null
 */
public record WishRequestResponse(
    String requestId,
    String type,
    String childId,
    String status,
    String requestedByType,
    String requestedById,
    String message,
    LocalDateTime requestedAt,
    String payerType,
    String payerId,
    LocalDateTime paidAt,
    String rejectReason,
    LocalDateTime rejectedAt,
    String cancelReason,
    LocalDateTime cancelledAt,
    List<Line> items,
    List<Purchase> purchases,
    List<String> paidOrderItemIds,
    LocalDateTime expiresAt,
    LocalDateTime expiredAt) {

    /**
     * @param cartItemId 장바구니 칸 ID. #999 전 조르기는 null
     * @param quantity 보낸 때 칸 수량
     * @param unitPrice 지금 가격. 상품 정보가 없으면 null
     * @param saleStatus 지금 판매 상태 값(ON_SALE · SOLD_OUT · STOPPED 등). 판매 중이 아니어도 빼지 않는다(화면이 버튼을 막는다)
     * @param purchased 결제돼 장바구니에서 빠졌는지
     * @param thumbnailUrl 상품 썸네일 URL(#1328, 장바구니와 같은 지금 상품 복사본 값). 없으면 null
     */
    public record Line(
        String cartItemId,
        String optionId,
        String productId,
        String sellerId,
        String productName,
        String optionName,
        String size,
        String sellerName,
        int quantity,
        Long unitPrice,
        boolean purchasable,
        String saleStatus,
        boolean purchased,
        String thumbnailUrl) {

        static Line from(WishRequestView.Line view) {
            return new Line(toText(view.cartItemId()), toText(view.optionId()), toText(view.productId()),
                toText(view.sellerId()), view.productName(), view.optionName(), view.size(), view.sellerName(),
                view.quantity(), view.unitPrice(), view.purchasable(), view.saleStatus(), view.purchased(),
                view.thumbnailUrl());
        }
    }

    /**
     * @param quantity 그 칸에서 이번에 산 수량(칸에 남은 수량 아님)
     */
    public record Purchase(String orderId, String cartItemId, int quantity, LocalDateTime purchasedAt) {
    }

    public static WishRequestResponse from(WishRequestView view) {
        return new WishRequestResponse(toText(view.requestId()), view.type(), toText(view.childId()), view.status(),
            view.requestedByType(), toText(view.requestedById()), view.message(), view.requestedAt(),
            view.payerType(), toText(view.payerId()), view.paidAt(), view.rejectReason(), view.rejectedAt(),
            view.cancelReason(), view.cancelledAt(), view.items().stream().map(Line::from).toList(),
            view.purchases().stream()
                .map(purchase -> new Purchase(toText(purchase.orderId()), toText(purchase.cartItemId()),
                    purchase.quantity(), purchase.purchasedAt()))
                .toList(),
            view.paidOrderItemIds().stream().map(String::valueOf).toList(),
            view.expiresAt(), view.expiredAt());
    }

    private static String toText(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
