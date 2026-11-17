package com.bbcc.kidly.boundedcontext.product.app.port.outbox;

import com.bbcc.kidly.shared.event.DomainEvent;

/**
 * 이벤트를 발행한 업무 트랜잭션 안에서 불러야 하며, 구현은 트랜잭션이 없으면 실패한다.
 */
public interface ProductEventOutbox {

    void append(DomainEvent event);
}
