package com.bbcc.kidly.boundedcontext.payment.out.kafka;

import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentEventOutbox;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentOutboxRepository;
import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 결제 이벤트를 kidly.payment.events로 보낼 메시지로 저장한다(#550).
 * 키는 correlationId(결제는 주문 ID, 정산 지급은 정산서 ID)라 같은 대상의 이벤트가 같은 파티션에 순서대로 쌓이고,
 * 본문은 {@link PaymentEventEnvelope} JSON이다.
 */
@Component
@RequiredArgsConstructor
public class PaymentOutboxWriter implements PaymentEventOutbox {

    static final String TOPIC = "kidly.payment.events";

    private final PaymentOutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;

    // MANDATORY는 트랜잭션을 새로 열지 않고 이벤트를 발행한 업무 트랜잭션에 합류만 한다.
    // 그래서 업무가 롤백되면 메시지도 함께 사라지고, 트랜잭션 밖에서 부르면 바로 실패한다.
    // 같은 이벤트를 다시 발행하면(동시에 포함) 메시지를 하나 더 만들지 않는다. 같은 eventId는 한 번만 보낸다
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(DomainEvent event) {
        outboxRepository.saveIfAbsent(event.eventId(), event.eventType(), TOPIC, event.correlationId(),
            jsonMapper.writeValueAsString(PaymentEventEnvelope.from(event)), LocalDateTime.now());
    }
}
