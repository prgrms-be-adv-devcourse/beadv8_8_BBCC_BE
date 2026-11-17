package com.bbcc.kidly.boundedcontext.product.out.outbox;

import com.bbcc.kidly.boundedcontext.product.app.port.outbox.ProductEventOutbox;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;
import com.bbcc.kidly.shared.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class JpaProductEventOutbox implements ProductEventOutbox {

    static final String TOPIC = "kidly.product.events";

    private final JpaProductOutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(DomainEvent event) {
        if (outboxRepository.existsByEventId(event.eventId())) {
            return;
        }
        outboxRepository.save(ProductOutboxMessage.create(new ProductOutboxMessage.CreateInfo(
            event.eventId(), event.eventType(), TOPIC, event.correlationId(),
            jsonMapper.writeValueAsString(ProductEventEnvelope.from(event)))));
    }
}
