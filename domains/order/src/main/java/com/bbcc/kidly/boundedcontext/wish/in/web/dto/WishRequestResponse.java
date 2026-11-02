package com.bbcc.kidly.boundedcontext.wish.in.web.dto;

import com.bbcc.kidly.boundedcontext.wish.app.dto.WishRequestView;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 조르기 한 건(#701). ID는 문자열(장바구니와 같음). 가격 · 판매 상태는 지금 값이다. 칸은 추가만 한다.
 *
 * @param status REQUESTED · APPROVED · REJECTED
 * @param requestedBy 보낸 사람 종류(PARENT · CHILD)
 * @param checkout ORDER(바로 결제) · CART(보호자 장바구니로 옮기기). 승인 전 · 거절이면 null
 * @param orderId 바로 결제로 만든 주문 ID. 없으면 null
 * @param orderResult 바로 결제한 주문의 결과(PAID · CANCELLED). 정해지기 전이면 null
 * @param rejectReason 거절 사유. 거절이 아니면 null
 */
public record WishRequestResponse(
    String requestId,
    String childId,
    String status,
    String requestedBy,
    String message,
    LocalDateTime requestedAt,
    String checkout,
    String orderId,
    String orderResult,
    String rejectReason,
    LocalDateTime decidedAt,
    List<Line> items) {

    /**
     * @param approvedQuantity 승인 수량. 승인하지 않은 줄 · 처리 전이면 null
     * @param unitPrice 지금 가격. 상품 정보가 없으면 null
     * @param saleStatus 지금 판매 상태 값(ON_SALE · SOLD_OUT · STOPPED 등). 판매 중이 아니어도 빼지 않는다(화면이 버튼을 막는다)
     */
    public record Line(
        String wishItemId,
        String optionId,
        String productId,
        String sellerId,
        String productName,
        String optionName,
        String size,
        String sellerName,
        int requestedQuantity,
        Integer approvedQuantity,
        Long unitPrice,
        boolean purchasable,
        String saleStatus) {

        static Line from(WishRequestView.Line view) {
            return new Line(toText(view.wishItemId()), toText(view.optionId()), toText(view.productId()),
                toText(view.sellerId()), view.productName(), view.optionName(), view.size(), view.sellerName(),
                view.requestedQuantity(), view.approvedQuantity(), view.unitPrice(), view.purchasable(),
                view.saleStatus());
        }
    }

    public static WishRequestResponse from(WishRequestView view) {
        return new WishRequestResponse(toText(view.requestId()), toText(view.childId()), view.status(),
            view.requestedBy(), view.message(), view.requestedAt(), view.checkout(), toText(view.orderId()),
            view.orderResult(), view.rejectReason(), view.decidedAt(),
            view.items().stream().map(Line::from).toList());
    }

    private static String toText(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
