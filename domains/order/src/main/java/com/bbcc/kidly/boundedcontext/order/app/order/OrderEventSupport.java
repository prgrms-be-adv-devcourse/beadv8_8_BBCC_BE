package com.bbcc.kidly.boundedcontext.order.app.order;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventOutbox;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 주문을 바꾸는 서비스(OrderService · OrderPlacementService · OrderConfirmationService)가 함께 쓰는 도우미(#1508). 도우미를
 * 서비스마다 복사하지 않으려고 따로 뺐다. 트랜잭션을 걸지 않으므로 부르는 서비스의 트랜잭션 안에서 그대로 돈다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OrderEventSupport {

    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;
    private final OrderEventOutbox outbox;

    /**
     * 이벤트를 Spring 이벤트로 발행하고, 같은 트랜잭션에서 Kafka로 보낼 메시지로도 저장한다(#540, 아웃박스).
     * 주문이 발행하는 이벤트는 모두 여기로 보낸다.
     */
    void publish(DomainEvent event) {
        eventPublisher.publish(event);
        outbox.append(event);
    }

    /**
     * 받은 이벤트(또는 시간 작업)의 주문을 찾는다. 없으면 WARN을 남기고 null.
     */
    Order findForEvent(Long orderId, String event) {
        return orderRepository.findById(orderId).orElseGet(() -> {
            log.warn("이벤트의 주문 없음: event={} orderId={}", event, orderId);
            return null;
        });
    }
}
