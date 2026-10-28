package com.bbcc.kidly.global.eventpublisher;

import com.bbcc.kidly.shared.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 도메인 이벤트를 스프링 이벤트({@link DomainEventListener})와 Kafka 양쪽으로 발행한다.
 * 받는 쪽은 구독 메서드마다 둘 중 하나로만 받는다. 둘 다 발행한 트랜잭션이 커밋된 뒤에만 전달된다.
 */
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final DomainEventSender domainEventSender;

    public void publish(DomainEvent event) {
        applicationEventPublisher.publishEvent(event);
        // 트랜잭션 밖이면 @DomainEventListener(AFTER_COMMIT)처럼 보내지 않는다
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    domainEventSender.send(event);
                }
            });
        }
    }
}
