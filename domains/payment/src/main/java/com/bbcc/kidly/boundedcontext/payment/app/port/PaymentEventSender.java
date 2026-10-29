package com.bbcc.kidly.boundedcontext.payment.app.port;

import com.bbcc.kidly.shared.event.DomainEvent;

/**
 * 결제가 발행한 도메인 간 이벤트를 Kafka 토픽 kidly.payment.events로 보낸다. 키는 correlationId다.
 * 보내지 못해도 예외를 던지지 않는다(이미 커밋된 업무를 실패로 만들지 않는다).
 */
public interface PaymentEventSender {

    void send(DomainEvent event);
}
