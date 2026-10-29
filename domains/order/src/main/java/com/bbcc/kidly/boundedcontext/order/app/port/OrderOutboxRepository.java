package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.outbox.OrderOutboxMessage;

/**
 * Kafka로 보낼 주문 이벤트 메시지 저장소(#540). 구현은 out/persistence (Spring Data가 만든다).
 */
public interface OrderOutboxRepository {

    OrderOutboxMessage save(OrderOutboxMessage message);
}
