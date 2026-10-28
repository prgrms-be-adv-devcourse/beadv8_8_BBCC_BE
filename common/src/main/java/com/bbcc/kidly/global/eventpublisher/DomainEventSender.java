package com.bbcc.kidly.global.eventpublisher;

import com.bbcc.kidly.shared.event.DomainEvent;

/**
 * 커밋된 도메인 이벤트를 프로세스 밖(Kafka)으로 보낸다.
 * 발행한 쪽은 이미 커밋했으므로 실패해도 예외를 던지지 않고 로그만 남긴다.
 */
@FunctionalInterface
public interface DomainEventSender {

    void send(DomainEvent event);
}
