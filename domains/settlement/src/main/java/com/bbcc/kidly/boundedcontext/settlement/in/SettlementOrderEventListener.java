package com.bbcc.kidly.boundedcontext.settlement.in;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementItemService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 주문상품이 구매확정되면 정산 대상으로 쌓는다.
 * Spring 이벤트로 받는다. Kafka 수신 스위치(kidly.settlement.kafka.consumer.enabled)를 끈 경우에만 뜬다.
 * 켜져 있으면 Kafka 리스너(SettlementOrderKafkaListener)가 받으므로, 같은 이벤트를 양쪽에서 받지 않는다.
 */
@Component
@ConditionalOnProperty(name = "kidly.settlement.kafka.consumer.enabled", havingValue = "false")
@RequiredArgsConstructor
public class SettlementOrderEventListener {

    private final SettlementItemService settlementItemService;

    @DomainEventListener
    public void on(OrderItemPurchaseConfirmedEvent event) {
        settlementItemService.register(event.data());
    }
}
