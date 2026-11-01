package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.shared.event.DomainEvent;

/**
 * 주문 모듈이 발행하는 이벤트(주문 · 장바구니, #540 · #654)를 Kafka로 보낼 메시지로 저장한다(아웃박스, 키 = correlationId).
 * 구현은 out/outbox.
 * 발행하는 업무 트랜잭션 안에서 불러야 한다(트랜잭션이 없으면 실패). 업무가 롤백되면 메시지도 남지 않는다.
 */
public interface OrderEventOutbox {

    void append(DomainEvent event);
}
