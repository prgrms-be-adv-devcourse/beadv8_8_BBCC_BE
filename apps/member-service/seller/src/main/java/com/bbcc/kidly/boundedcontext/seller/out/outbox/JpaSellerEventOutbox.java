package com.bbcc.kidly.boundedcontext.seller.out.outbox;

import com.bbcc.kidly.boundedcontext.seller.app.port.outbox.SellerEventOutbox;
import com.bbcc.kidly.boundedcontext.seller.domain.outbox.SellerOutboxMessage;
import com.bbcc.kidly.shared.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class JpaSellerEventOutbox implements SellerEventOutbox {

    private final JpaSellerOutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final ApplicationEventPublisher eventPublisher;

    // MANDATORY는 트랜잭션을 새로 열지 않고 이벤트를 발행한 업무 트랜잭션에 합류만 한다.
    // 그래서 업무가 롤백되면 메시지도 함께 사라지고, 트랜잭션 밖에서 부르면 바로 실패한다.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(DomainEvent event) {
        var payload = jsonMapper.writeValueAsString(SellerEventEnvelope.from(event));
        var message = outboxRepository.save(SellerOutboxMessage.create(new SellerOutboxMessage.CreateInfo(
            event.eventId(), event.eventType(), SellerOutboxKafkaConfig.TOPIC, event.correlationId(), payload)));
        eventPublisher.publishEvent(message);
    }
}
