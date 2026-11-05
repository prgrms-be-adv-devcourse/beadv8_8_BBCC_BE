package com.bbcc.kidly.boundedcontext.notification.app;

import java.util.Map;
import java.util.Optional;

/**
 * 구매자가 직접 하지 않은 주문 취소의 알림함 문구. 결제한 돈이 있으면 환불 완료 메일이 따로 간다.
 * 구매자가 직접 취소했거나 결제가 실패한 주문은 그 화면에서 결과를 바로 보므로 알리지 않는다(비어 있음).
 */
public record OrderCancelInbox(String title, String body) {

    private static final Map<String, OrderCancelInbox> ORDER_CANCELLED = Map.of(
        "OUT_OF_STOCK", new OrderCancelInbox("재고가 없어 주문이 취소됐어요",
            "결제한 금액은 예치금으로 돌려드려요."),
        "PAYMENT_EXPIRED", new OrderCancelInbox("결제 기한이 지나 주문이 취소됐어요",
            "결제가 끝나지 않은 채 기한이 지나 주문을 취소했어요."));

    private static final Map<String, String> SELLER_REASONS = Map.of(
        "OUT_OF_STOCK", "재고 부족",
        "WRONG_PRODUCT_INFO", "상품 정보 오류",
        "OTHER", "판매자 사정");

    public static Optional<OrderCancelInbox> orderCancelled(String reason) {
        return Optional.ofNullable(ORDER_CANCELLED.get(reason));
    }

    // 사유 이름을 모르면(새로 생긴 사유) 사유 없이 쓴다
    public static OrderCancelInbox itemsCancelledBySeller(String reason, int itemCount) {
        var label = SELLER_REASONS.get(reason);
        var body = "상품 %d개가 취소됐어요.%s 금액은 예치금으로 돌려드려요."
            .formatted(itemCount, label == null ? "" : " 사유: " + label + ".");
        return new OrderCancelInbox("판매자가 주문 상품을 취소했어요", body);
    }
}
