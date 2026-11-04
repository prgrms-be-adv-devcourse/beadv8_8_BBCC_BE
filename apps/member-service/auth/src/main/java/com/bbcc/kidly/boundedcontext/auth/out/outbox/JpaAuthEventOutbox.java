package com.bbcc.kidly.boundedcontext.auth.out.outbox;

import com.bbcc.kidly.boundedcontext.auth.app.port.AuthEventOutbox;
import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxMessage;
import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxRepository;
import com.bbcc.kidly.boundedcontext.auth.out.kafka.AuthEventEnvelope;
import com.bbcc.kidly.shared.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Repository
@RequiredArgsConstructor
class JpaAuthEventOutbox implements AuthEventOutbox {
    private final AuthOutboxRepository repository;
    private final JsonMapper jsonMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(DomainEvent event) {
        repository.save(AuthOutboxMessage.create(event.eventId(), event.eventType(), event.correlationId(),
            AuthEventEnvelope.toJson(jsonMapper, event)));
    }
}
