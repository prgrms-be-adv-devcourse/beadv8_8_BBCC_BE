package com.bbcc.kidly.boundedcontext.payment.in.event;

import com.bbcc.kidly.boundedcontext.payment.app.PaymentEventHandler;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderItemCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.order.events에서 order.payment-requested · order.cancelled · order-item.cancelled를 받아 결제 · 환불한다(#593, #711).
 * 주문의 다른 종류(생성 · 구매확정 · 장바구니 담기)는 건너뛴다. 키(주문 ID)가 같아 한 주문의 결제 요청 · 취소는 보낸 순서대로 처리한다.
 * 스위치(kidly.payment.kafka.consumer.enabled)가 꺼져 있으면 시작하지 않고, 그때는 Spring 리스너가 받는다.
 * order-item.cancelled는 Spring 리스너가 없어, 스위치를 끈 동안은 토픽에 남아 있다가 다시 켜면 받는다.
 */
@Component
@RequiredArgsConstructor
public class PaymentOrderEventListener {

    private final PaymentEventHandler handler;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "paymentOrderEventListener", topics = PaymentKafkaListenerConfig.ORDER_EVENTS,
        groupId = PaymentKafkaListenerConfig.GROUP, containerFactory = "paymentKafkaListenerContainerFactory",
        autoStartup = PaymentKafkaListenerConfig.AUTO_STARTUP)
    public void on(ConsumerRecord<String, String> record) {
        switch (PaymentKafkaListenerConfig.eventType(record)) {
            case "order.payment-requested" -> handler.orderPaymentRequested(
                jsonMapper.readValue(record.value(), OrderPaymentRequestedEvent.class));
            case "order.cancelled" ->
                handler.orderCancelled(jsonMapper.readValue(record.value(), OrderCancelledEvent.class));
            case "order-item.cancelled" ->
                handler.orderItemCancelled(jsonMapper.readValue(record.value(), OrderItemCancelledEvent.class));
            default -> { }  // 결제가 받지 않는 종류
        }
    }
}
