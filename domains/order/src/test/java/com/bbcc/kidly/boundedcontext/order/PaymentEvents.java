package com.bbcc.kidly.boundedcontext.order;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 결제 도메인이 order.payment-requested를 받아 보내던 결제 결과 이벤트(#616). 주문 모듈 테스트에는 결제 도메인이 없으므로
 * 테스트가 이것으로 만들어 넣는다. 결제 ID는 주문 ID를 그대로 쓴다(주문마다 다르면 된다).
 */
public final class PaymentEvents {

    private PaymentEvents() {
    }

    /**
     * 주문한 회원 · 총액 · 주문상품별 금액이 모두 주문과 같은 결제 완료.
     */
    public static PaymentCompletedEvent completed(OrderDetail order, Long memberId) {
        return new PaymentCompletedEvent(UUID.randomUUID(), LocalDateTime.now(), new PaymentCompletedEvent.Data(
            order.orderId(), order.orderId(), memberId, order.totalAmount(),
            order.items().stream()
                .map(item -> new PaymentCompletedEvent.Item(item.orderItemId(), item.amount()))
                .toList(),
            LocalDateTime.now()));
    }

    /**
     * 잔액 부족 결제 실패. 결제 도메인은 지갑이 없을 때도 잔액 0인 지갑을 만들어 이것으로 실패하고, 실패 결제를 저장하므로 결제 ID가 있다.
     */
    public static PaymentFailedEvent insufficientBalance(OrderDetail order, Long memberId) {
        return new PaymentFailedEvent(UUID.randomUUID(), LocalDateTime.now(), new PaymentFailedEvent.Data(
            order.orderId(), order.orderId(), memberId, order.totalAmount(), PaymentFailureCode.INSUFFICIENT_BALANCE,
            LocalDateTime.now()));
    }
}
