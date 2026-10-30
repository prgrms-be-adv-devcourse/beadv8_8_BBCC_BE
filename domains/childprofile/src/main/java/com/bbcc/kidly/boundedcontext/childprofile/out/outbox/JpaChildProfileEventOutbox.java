package com.bbcc.kidly.boundedcontext.childprofile.out.outbox;

import com.bbcc.kidly.boundedcontext.childprofile.app.port.outbox.ChildProfileEventOutbox;
import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxMessage;
import com.bbcc.kidly.shared.event.DomainEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 자녀 프로필 이벤트를 kidly.childprofile.events로 보낼 메시지로 저장한다.
 * 키는 correlationId(자녀 ID)라 같은 자녀의 이벤트가 같은 파티션에 순서대로 쌓이고, 본문은 {@link ChildProfileEventEnvelope} JSON이다.
 */
@Component
public class JpaChildProfileEventOutbox implements ChildProfileEventOutbox {

    static final String TOPIC = "kidly.childprofile.events";

    private final JpaChildProfileOutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;

    public JpaChildProfileEventOutbox(JpaChildProfileOutboxRepository outboxRepository, JsonMapper jsonMapper) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
    }

    // MANDATORY는 트랜잭션을 새로 열지 않고 이벤트를 발행한 업무 트랜잭션에 합류만 한다.
    // 그래서 업무가 롤백되면 메시지도 함께 사라지고, 트랜잭션 밖에서 부르면 바로 실패한다.
    // 같은 이벤트를 다시 발행하면 메시지를 하나 더 만들지 않는다. 받는 쪽 테스트가 중복 수신을 보려고 같은 이벤트를 두 번 발행하는데
    // (예: 주문 ReplicaEventIntegrationTest), 그때 event_id UNIQUE에 걸려 업무까지 롤백되지 않게 한다.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(DomainEvent event) {
        if (outboxRepository.existsByEventId(event.eventId())) {
            return;
        }
        var payload = jsonMapper.writeValueAsString(ChildProfileEventEnvelope.from(event));
        outboxRepository.save(ChildProfileOutboxMessage.create(new ChildProfileOutboxMessage.CreateInfo(
            event.eventId(), event.eventType(), TOPIC, event.correlationId(), payload)));
    }
}
