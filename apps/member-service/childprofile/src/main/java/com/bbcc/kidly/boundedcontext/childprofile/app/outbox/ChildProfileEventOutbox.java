package com.bbcc.kidly.boundedcontext.childprofile.app.outbox;

import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxMessage;
import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxRepository;
import com.bbcc.kidly.member.support.outbox.EventOutbox;
import com.bbcc.kidly.shared.event.DomainEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ChildProfileEventOutbox extends EventOutbox<ChildProfileOutboxMessage> {

    private final ChildProfileOutboxRepository repository;

    public ChildProfileEventOutbox(ChildProfileOutboxRepository repository, JsonMapper jsonMapper,
        ApplicationEventPublisher eventPublisher) {
        super(repository, ChildProfileOutboxMessage::new, ChildProfileOutboxMessage.TOPIC,
            jsonMapper::writeValueAsString, eventPublisher);
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(DomainEvent event) {
        if (repository.existsByEventId(event.eventId())) {
            return;
        }
        super.append(event);
    }
}
