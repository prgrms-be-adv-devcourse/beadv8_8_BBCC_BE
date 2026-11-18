package com.bbcc.kidly.boundedcontext.auth.app.outbox;

import com.bbcc.kidly.boundedcontext.auth.app.port.outbox.AuthOutboxSender;
import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxMessage;
import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxRepository;
import com.bbcc.kidly.member.support.outbox.OutboxRelay;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
public class AuthOutboxRelay extends OutboxRelay<AuthOutboxMessage> {

    public AuthOutboxRelay(AuthOutboxRepository repository, AuthOutboxSender sender,
        TransactionTemplate transactionTemplate) {
        super("인증", "auth-outbox-dispatcher", repository, sender, transactionTemplate);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(AuthOutboxMessage message) {
        enqueue(message);
    }
}
