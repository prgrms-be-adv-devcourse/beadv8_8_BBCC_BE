package com.bbcc.kidly.boundedcontext.order.app.provided;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventOutbox;
import com.bbcc.kidly.boundedcontext.order.app.port.in.OrderEventIssuer;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 컨텍스트의 이벤트를 주문 이벤트(OrderService)와 같은 순서로 낸다(#654, {@link OrderEventIssuer}):
 * Spring 이벤트 발행, 같은 트랜잭션에서 아웃박스 저장. Kafka 보내기가 꺼져도 둘 다 그대로다(릴레이만 없다).
 */
@Service
@RequiredArgsConstructor
public class ProvidedEventService implements OrderEventIssuer {

    private final EventPublisher eventPublisher;
    private final OrderEventOutbox outbox;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void issue(DomainEvent event) {
        eventPublisher.publish(event);
        outbox.append(event);
    }
}
