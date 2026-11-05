package com.bbcc.kidly.boundedcontext.member.out.outbox;

import com.bbcc.kidly.boundedcontext.member.app.port.outbox.MemberEventOutbox;
import com.bbcc.kidly.boundedcontext.member.domain.outbox.MemberOutboxMessage;
import com.bbcc.kidly.boundedcontext.member.out.kafka.KafkaMemberEventSender;
import com.bbcc.kidly.shared.event.DomainEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회원 탈퇴 이벤트를 kidly.member.events로 보낼 메시지로 저장한다.
 * 키는 correlationId(회원 ID)라 같은 회원의 이벤트가 같은 파티션에 쌓이고, 본문은 {@link MemberEventEnvelope} JSON이다.
 * 커밋 뒤 바로 보내는 가입 · 변경과 본문 모양이 같다.
 */
@Component
public class JpaMemberEventOutbox implements MemberEventOutbox {

    private final JpaMemberOutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;

    public JpaMemberEventOutbox(JpaMemberOutboxRepository outboxRepository, JsonMapper jsonMapper) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
    }

    // MANDATORY는 트랜잭션을 새로 열지 않고 이벤트를 발행한 업무 트랜잭션에 합류만 한다.
    // 그래서 업무가 롤백되면 메시지도 함께 사라지고, 트랜잭션 밖에서 부르면 바로 실패한다.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(DomainEvent event) {
        var payload = jsonMapper.writeValueAsString(MemberEventEnvelope.from(event));
        outboxRepository.save(MemberOutboxMessage.create(new MemberOutboxMessage.CreateInfo(
            event.eventId(), event.eventType(), KafkaMemberEventSender.TOPIC, event.correlationId(), payload)));
    }
}
