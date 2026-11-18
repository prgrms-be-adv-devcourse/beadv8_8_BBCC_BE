package com.bbcc.kidly.boundedcontext.auth.app.outbox;

import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxMessage;
import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxRepository;
import com.bbcc.kidly.member.support.outbox.EventOutbox;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AuthEventOutbox extends EventOutbox<AuthOutboxMessage> {

    public AuthEventOutbox(AuthOutboxRepository repository, JsonMapper jsonMapper,
        ApplicationEventPublisher eventPublisher) {
        super(repository, AuthOutboxMessage::new, AuthOutboxMessage.TOPIC, jsonMapper::writeValueAsString,
            eventPublisher);
    }
}
