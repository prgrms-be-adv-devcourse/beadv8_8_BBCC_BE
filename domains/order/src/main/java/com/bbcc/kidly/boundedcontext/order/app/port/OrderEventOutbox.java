package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.shared.event.DomainEvent;

/**
 * 주문이 발행하는 이벤트를 Kafka로 보낼 메시지로 저장한다(#540, 아웃박스). 구현은 out/messaging.
 * 발행하는 업무 트랜잭션 안에서 불러야 한다(트랜잭션이 없으면 실패). 업무가 롤백되면 메시지도 남지 않는다.
 */
public interface OrderEventOutbox {

    void append(DomainEvent event);
}
