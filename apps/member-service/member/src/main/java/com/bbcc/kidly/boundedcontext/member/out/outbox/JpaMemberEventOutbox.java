package com.bbcc.kidly.boundedcontext.member.out.outbox;

import com.bbcc.kidly.boundedcontext.member.app.port.outbox.MemberEventOutbox;
import com.bbcc.kidly.boundedcontext.member.domain.outbox.MemberOutboxMessage;
import com.bbcc.kidly.shared.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class JpaMemberEventOutbox implements MemberEventOutbox {

    private final JpaMemberOutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final ApplicationEventPublisher eventPublisher;

    // MANDATORY는 트랜잭션을 새로 열지 않고 이벤트를 발행한 업무 트랜잭션에 합류만 한다.
    // 그래서 업무가 롤백되면 메시지도 함께 사라지고, 트랜잭션 밖에서 부르면 바로 실패한다.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(DomainEvent event) {
        var payload = jsonMapper.writeValueAsString(MemberEventEnvelope.from(event));
        var message = outboxRepository.save(MemberOutboxMessage.create(new MemberOutboxMessage.CreateInfo(
            event.eventId(), event.eventType(), MemberOutboxKafkaConfig.TOPIC, event.correlationId(), payload)));
        eventPublisher.publishEvent(message);
    }
}
