package com.bbcc.kidly.boundedcontext.seller.app.outbox;

import com.bbcc.kidly.boundedcontext.seller.domain.outbox.SellerOutboxMessage;
import com.bbcc.kidly.boundedcontext.seller.domain.outbox.SellerOutboxRepository;
import com.bbcc.kidly.member.support.outbox.EventOutbox;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SellerEventOutbox extends EventOutbox<SellerOutboxMessage> {

    public SellerEventOutbox(SellerOutboxRepository repository, JsonMapper jsonMapper,
        ApplicationEventPublisher eventPublisher) {
        super(repository, SellerOutboxMessage::new, SellerOutboxMessage.TOPIC, jsonMapper::writeValueAsString,
            eventPublisher);
    }
}
