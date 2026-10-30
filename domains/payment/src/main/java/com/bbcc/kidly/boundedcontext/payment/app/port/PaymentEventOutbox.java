package com.bbcc.kidly.boundedcontext.payment.app.port;

import com.bbcc.kidly.shared.event.DomainEvent;

/**
 * 결제가 발행한 이벤트를 Kafka로 보낼 메시지로 저장한다(#550, 아웃박스).
 * 발행한 업무 트랜잭션 안에서 불러야 한다(트랜잭션이 없으면 실패). 업무가 롤백되면 메시지도 남지 않는다.
 */
public interface PaymentEventOutbox {

    void append(DomainEvent event);
}
