package com.bbcc.kidly.boundedcontext.order.out.outbox;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventOutbox;
import com.bbcc.kidly.boundedcontext.order.out.config.OrderKafkaTopicConfig;
import com.bbcc.kidly.boundedcontext.order.out.kafka.OrderEventEnvelope;
import com.bbcc.kidly.boundedcontext.order.out.persistence.JpaOrderOutboxRepository;
import com.bbcc.kidly.shared.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문 모듈 이벤트(주문 · 장바구니)를 kidly.order.events로 보낼 메시지로 저장한다(#540 · #654). 키는 이벤트의 correlationId라
 * (주문 이벤트는 주문 ID, 장바구니 담기는 회원 ID) 같은 흐름의 이벤트가 같은 파티션에 순서대로 쌓이고, 본문은
 * {@link OrderEventEnvelope} JSON이다. 발행하는 업무 트랜잭션에 합류만 한다.
 */
@Component
@RequiredArgsConstructor
class OrderOutboxWriter implements OrderEventOutbox {

    private final JpaOrderOutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(DomainEvent event) {
        outboxRepository.save(OrderOutboxMessage.create(event.eventId(), event.eventType(),
            OrderKafkaTopicConfig.ORDER_EVENTS, event.correlationId(),
            jsonMapper.writeValueAsString(OrderEventEnvelope.from(event))));
    }
}
