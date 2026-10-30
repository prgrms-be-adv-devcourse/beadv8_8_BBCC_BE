package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.outbox.OrderOutboxMessage;

/**
 * 아웃박스 메시지 하나를 Kafka로 보낸다(#540). 구현은 out/messaging.
 * 브로커가 받았다고 답할 때까지 기다리고, 보내지 못하면(시간 초과 포함) 예외를 던진다. 예외 메시지에 본문을 넣지 않는다.
 */
public interface OrderEventSender {

    void send(OrderOutboxMessage message);
}
