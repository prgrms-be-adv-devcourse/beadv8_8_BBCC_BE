package com.bbcc.kidly.member.support.outbox;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.util.function.Function;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 도메인 이벤트를 Kafka로 보낼 메시지로 저장하고, 저장한 메시지를 이벤트로 알린다(커밋 뒤 바로 전송용).
 * 도메인 모듈의 빈이 상속해 자기 리포지토리 · 엔티티 · 토픽 · JSON 직렬화를 넘긴다.
 */
public abstract class EventOutbox<T extends OutboxMessage> {

    private final OutboxRepository<T> repository;
    private final Function<OutboxMessage.CreateInfo, T> factory;
    private final String topic;
    private final Function<EventEnvelope, String> toJson;
    private final ApplicationEventPublisher eventPublisher;

    protected EventOutbox(OutboxRepository<T> repository, Function<OutboxMessage.CreateInfo, T> factory,
        String topic, Function<EventEnvelope, String> toJson, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.factory = factory;
        this.topic = topic;
        this.toJson = toJson;
        this.eventPublisher = eventPublisher;
    }

    // MANDATORY는 트랜잭션을 새로 열지 않고 이벤트를 발행한 업무 트랜잭션에 합류만 한다.
    // 그래서 업무가 롤백되면 메시지도 함께 사라지고, 트랜잭션 밖에서 부르면 바로 실패한다.
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(DomainEvent event) {
        var payload = toJson.apply(EventEnvelope.from(event));
        var info = new OutboxMessage.CreateInfo(
            event.eventId(), event.eventType(), topic, event.correlationId(), payload);
        eventPublisher.publishEvent(repository.save(factory.apply(info)));
    }
}
