package com.bbcc.kidly.boundedcontext.childprofile.out.outbox;

import com.bbcc.kidly.boundedcontext.childprofile.app.port.outbox.ChildProfileEventOutbox;
import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxMessage;
import com.bbcc.kidly.shared.event.DomainEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
public class JpaChildProfileEventOutbox implements ChildProfileEventOutbox {

    static final String TOPIC = "kidly.childprofile.events";

    private final JpaChildProfileOutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;

    public JpaChildProfileEventOutbox(JpaChildProfileOutboxRepository outboxRepository, JsonMapper jsonMapper) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
    }

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
