package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.order.PaymentFailures;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.payment.events에서 payment.completed · payment.failed를 받아 지금 결제 처리를 그대로 부른다(#594).
 * 이 메서드는 트랜잭션을 열지 않는다(결제 처리는 반영 · 보관 · 취소가 각자 트랜잭션에서 커밋한다, #532).
 * <ul>
 *   <li>처리 실패를 주문 자체 보관(결제 완료 → payment_recovery, 결제 실패 → order_event_failure)에 남겼으면 처리 완료로 보고
 *       정상 반환한다(관리자가 재처리 · 종료). 보관까지 실패했으면 예외를 그대로 던져 재시도 → DLT로 보낸다</li>
 *   <li>결제 완료의 동시 처리에서 진 쪽(낙관적 락 · 배송 유니크)은 결제 처리가 주문을 다시 읽어 결과가 났을 때만(결제완료 ·
 *       취소) 처리 완료 표시를 붙인다 — 다시 하면 환불 확인 WARN(#519)이 겹치므로. 아직 결제대기면(장바구니 줄 동시 변경 등으로
 *       반영이 통째로 되돌려짐) 표시가 없어 다시 던진다(재시도에서 반영되거나, 재시도가 끝나면 보관 · 보관 실패면 DLT)</li>
 *   <li>결제 실패의 진 쪽은 처리 완료로 본다: 결제대기가 아니면 다시 처리해도 아무것도 안 한다</li>
 *   <li>결제 완료가 재시도까지 모두 실패하면 DLT로 보내기 전에 payment_recovery에 보관하고, 보관되면 DLT로 보내지 않는다
 *       ({@link #keepAfterRetries}, #632)</li>
 *   <li>본문을 읽지 못하면(모르는 enum 값 · eventType 헤더 없음) 재시도 없이 DLT로 간다(OrderKafkaListenerConfig)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class PaymentEventKafkaListener {

    // 주문 Kafka 리스너 이름 규칙: order + 받는 이벤트 + KafkaListener
    static final String ID = "orderPaymentEventKafkaListener";
    private static final String PAYMENT_COMPLETED = "payment.completed";

    private final JsonMapper jsonMapper;
    private final PaymentEventListener paymentEventListener;

    @KafkaListener(id = ID, topics = OrderKafkaListenerConfig.PAYMENT_EVENTS, groupId = OrderKafkaListenerConfig.GROUP,
        containerFactory = OrderKafkaListenerConfig.FACTORY)
    public void on(ConsumerRecord<String, String> record) {
        switch (OrderKafkaMessages.eventType(record)) {
            case PAYMENT_COMPLETED -> {
                PaymentCompletedEvent event = jsonMapper.readValue(record.value(), PaymentCompletedEvent.class);
                completeIf(() -> paymentEventListener.onPaymentCompleted(event), false);
            }
            case "payment.failed" -> {
                PaymentFailedEvent event = jsonMapper.readValue(record.value(), PaymentFailedEvent.class);
                completeIf(() -> paymentEventListener.onPaymentFailed(event), true);
            }
            default -> {
                // 주문이 받지 않는 종류(payment.refunded · 정산 지급 등)
            }
        }
    }

    /**
     * DLT로 보내기 전에 불린다(#632, OrderKafkaListenerConfig 에러 처리기): 재시도가 끝났거나 재시도하지 않는 예외다.
     * 결제 토픽의 payment.completed만 결제 완료 보관을 시도한다(돈은 빠졌는데 기록 없이 DLT로만 가면 만료가 취소할 수 있다).
     * 그 밖의 종류 · 읽지 못하는 메시지 · 원래 예외가 RuntimeException이 아니면 false(지금처럼 DLT).
     *
     * @param error 에러 처리기가 받은 예외(리스너 실패면 원래 예외로 벗겨 보관 이유로 쓴다)
     * @return 보관했으면 true(처리 완료)
     */
    boolean keepAfterRetries(ConsumerRecord<?, ?> record, Exception error) {
        if (!OrderKafkaListenerConfig.PAYMENT_EVENTS.equals(record.topic())
            || !PAYMENT_COMPLETED.equals(OrderKafkaMessages.header(record, OrderKafkaMessages.EVENT_TYPE))
            || !(record.value() instanceof String body)
            || !(listenerCause(error) instanceof RuntimeException cause)) {
            return false;
        }
        PaymentCompletedEvent event;
        try {
            event = jsonMapper.readValue(body, PaymentCompletedEvent.class);
        } catch (JacksonException e) {
            return false;
        }
        return paymentEventListener.keepAfterRetries(event, cause);
    }

    private static Throwable listenerCause(Exception error) {
        return error instanceof ListenerExecutionFailedException && error.getCause() != null ? error.getCause() : error;
    }

    // 보관됐거나 결과가 났다는 표시가 있으면 처리 완료. lostIsDone이면 동시 처리에서 진 쪽도 처리 완료로 본다
    private static void completeIf(Runnable process, boolean lostIsDone) {
        try {
            process.run();
        } catch (RuntimeException e) {
            boolean handedOff = EventFailureKeeper.isHandled(e);
            boolean done = handedOff || lostIsDone && PaymentFailures.isLostConcurrentDuplicate(e);
            if (!done) {
                throw e;
            }
            if (handedOff) {
                EventHandOffs.noteHandedOff();
            }
        }
    }
}
