package com.bbcc.kidly.boundedcontext.member.app.outbox;

import com.bbcc.kidly.boundedcontext.member.domain.outbox.MemberOutboxMessage;
import com.bbcc.kidly.boundedcontext.member.domain.outbox.MemberOutboxRepository;
import com.bbcc.kidly.member.support.outbox.EventOutbox;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class MemberEventOutbox extends EventOutbox<MemberOutboxMessage> {

    public MemberEventOutbox(MemberOutboxRepository repository, JsonMapper jsonMapper,
        ApplicationEventPublisher eventPublisher) {
        super(repository, MemberOutboxMessage::new, MemberOutboxMessage.TOPIC, jsonMapper::writeValueAsString,
            eventPublisher);
    }
}
