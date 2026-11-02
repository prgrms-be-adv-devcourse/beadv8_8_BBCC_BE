package com.bbcc.kidly.boundedcontext.review.in.event;

import com.bbcc.kidly.boundedcontext.review.app.port.in.ReviewEligibilityRecorder;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ReviewEligibilityRecorder.PurchaseConfirmed;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * order-item.purchase-confirmed를 받아 리뷰 작성 자격을 남긴다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 * 받는 곳: Kafka 리스너({@link CommunityOrderEventKafkaListener}),
 * 스위치가 꺼졌을 때 Spring 수신({@link CommunitySpringEventReceiver}).
 * 트랜잭션은 자격 저장이 따로 연다(유일 제약에 막혀도 부르는 쪽 트랜잭션이 깨지지 않게).
 */
@Component
@RequiredArgsConstructor
public class CommunityOrderEventListener {

    private final ReviewEligibilityRecorder reviewEligibilityRecorder;

    /**
     * 자격을 만들 수 없는 값(데이터 · 주문상품 ID · 회원 ID · 상품 ID · 발생 시각)이 없으면 WARN 한 줄을 남기고 건너뛴다.
     * 구매확정 시각이 없으면 이벤트 발생 시각을 쓴다.
     */
    public void onPurchaseConfirmed(OrderItemPurchaseConfirmedEvent event) {
        if (CommunityEventGuard.accept("구매확정", event, "orderItemId", orderItemIdOf(event),
            missingRequiredValue(event))) {
            reviewEligibilityRecorder.record(toConfirmed(event));
        }
    }

    private static String missingRequiredValue(OrderItemPurchaseConfirmedEvent event) {
        OrderItemPurchaseConfirmedEvent.Data data = event.data();
        if (data == null) {
            return "missing-data";
        }
        if (data.orderItemId() == null) {
            return "missing-order-item-id";
        }
        if (data.memberId() == null) {
            return "missing-member-id";
        }
        if (data.productId() == null) {
            return "missing-product-id";
        }
        if (event.occurredAt() == null) {
            return "missing-occurred-at";
        }
        return null;
    }

    private static Long orderItemIdOf(OrderItemPurchaseConfirmedEvent event) {
        return event.data() == null ? null : event.data().orderItemId();
    }

    private static PurchaseConfirmed toConfirmed(OrderItemPurchaseConfirmedEvent event) {
        OrderItemPurchaseConfirmedEvent.Data data = event.data();
        return new PurchaseConfirmed(data.orderItemId(), data.orderId(), data.memberId(), data.productId(),
            data.optionId(), data.size(), data.confirmedAt() == null ? event.occurredAt() : data.confirmedAt());
    }
}
