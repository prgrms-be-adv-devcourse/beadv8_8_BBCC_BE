package com.bbcc.kidly.boundedcontext.seller.app.port;

import com.bbcc.kidly.shared.event.DomainEvent;

/**
 * 판매자가 발행한 도메인 간 이벤트를 Kafka 토픽 kidly.seller.events로 보낸다. 키는 correlationId(판매자 ID)다.
 * 보내지 못해도 예외를 던지지 않는다(이미 커밋된 업무를 실패로 만들지 않는다).
 */
public interface SellerEventSender {

    void send(DomainEvent event);
}
