package com.bbcc.kidly.boundedcontext.childprofile.app.port.outbox;

import com.bbcc.kidly.shared.event.DomainEvent;

/**
 * 자녀 프로필이 발행한 이벤트를 Kafka로 보낼 메시지로 저장한다.
 * 이벤트를 발행한 업무 트랜잭션 안에서 불러야 하며, 구현은 트랜잭션이 없으면 실패한다.
 */
public interface ChildProfileEventOutbox {

    void append(DomainEvent event);
}
