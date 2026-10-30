package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.order.PaymentFailures;
import com.bbcc.kidly.boundedcontext.order.app.port.PaymentEventListenerState;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.payment.events에서 payment.completed · payment.failed를 받아 지금 결제 처리를 그대로 부른다(#594).
 * 이 메서드는 트랜잭션을 열지 않는다(결제 처리는 반영 · 보관 · 취소가 각자 트랜잭션에서 커밋한다, #532).
 * <ul>
 *   <li>처리 실패를 주문 자체 보관(결제 완료 → payment_recovery, 결제 실패 → order_event_failure)에 남겼으면 처리 완료로 보고
 *       정상 반환한다(관리자가 재처리 · 종료). 보관까지 실패했으면 예외를 그대로 던져 재시도 → DLT로 보낸다</li>
 *   <li>동시 처리에서 진 쪽(낙관적 락 · 배송 유니크)도 처리 완료로 본다: 이긴 쪽이 이미 반영 · 취소했고, 다시 하면 취소된 주문
 *       분기에서 환불 확인 WARN(#519)이 한 번 더 남는다</li>
 *   <li>본문을 읽지 못하면(모르는 enum 값 · eventType 헤더 없음) 재시도 없이 DLT로 간다(OrderKafkaListenerConfig)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class PaymentEventKafkaListener {

    static final String ID = PaymentEventListenerState.LISTENER_ID;

    private final JsonMapper jsonMapper;
    private final PaymentEventListener paymentEventListener;

    @KafkaListener(id = ID, topics = OrderKafkaListenerConfig.PAYMENT_EVENTS, groupId = OrderKafkaListenerConfig.GROUP,
        containerFactory = OrderKafkaListenerConfig.FACTORY)
    public void on(ConsumerRecord<String, String> record) {
        switch (OrderKafkaMessages.eventType(record)) {
            case "payment.completed" -> {
                PaymentCompletedEvent event = jsonMapper.readValue(record.value(), PaymentCompletedEvent.class);
                completeIfHandled(() -> paymentEventListener.onPaymentCompleted(event));
            }
            case "payment.failed" -> {
                PaymentFailedEvent event = jsonMapper.readValue(record.value(), PaymentFailedEvent.class);
                completeIfHandled(() -> paymentEventListener.onPaymentFailed(event));
            }
            default -> {
                // 주문이 받지 않는 종류(payment.refunded · 정산 지급 등)
            }
        }
    }

    private static void completeIfHandled(Runnable process) {
        try {
            process.run();
        } catch (RuntimeException e) {
            if (!EventFailureKeeper.isKept(e) && !PaymentFailures.isLostConcurrentDuplicate(e)) {
                throw e;
            }
        }
    }
}
