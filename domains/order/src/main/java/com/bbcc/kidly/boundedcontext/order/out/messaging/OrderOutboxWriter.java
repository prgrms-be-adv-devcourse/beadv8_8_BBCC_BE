package com.bbcc.kidly.boundedcontext.order.out.messaging;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventOutbox;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.order.domain.outbox.OrderOutboxMessage;
import com.bbcc.kidly.boundedcontext.order.out.config.OrderKafkaTopicConfig;
import com.bbcc.kidly.shared.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문 이벤트를 kidly.order.events로 보낼 메시지로 저장한다(#540). 키는 correlationId(주문 ID)라 한 주문의 이벤트가
 * 같은 파티션에 순서대로 쌓이고, 본문은 {@link OrderEventEnvelope} JSON이다. 발행하는 업무 트랜잭션에 합류만 한다.
 */
@Component
@RequiredArgsConstructor
class OrderOutboxWriter implements OrderEventOutbox {

    private final OrderOutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(DomainEvent event) {
        outboxRepository.save(OrderOutboxMessage.create(event.eventId(), event.eventType(),
            OrderKafkaTopicConfig.ORDER_EVENTS, event.correlationId(),
            jsonMapper.writeValueAsString(OrderEventEnvelope.from(event))));
    }
}
