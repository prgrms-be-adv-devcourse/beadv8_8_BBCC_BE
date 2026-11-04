package com.bbcc.kidly.boundedcontext.auth.app.port;

import com.bbcc.kidly.shared.event.DomainEvent;

/**
 * 인증이 발행한 도메인 간 이벤트를 Kafka 토픽 kidly.auth.events로 보낸다. 키는 correlationId(주체 ID)다.
 * 보내지 못해도 예외를 던지지 않는다(이미 커밋된 업무를 실패로 만들지 않는다).
 */
public interface AuthEventSender {

    void send(DomainEvent event);
}
