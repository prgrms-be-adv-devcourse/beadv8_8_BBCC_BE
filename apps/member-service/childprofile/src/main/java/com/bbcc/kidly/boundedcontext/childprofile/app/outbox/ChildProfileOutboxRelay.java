package com.bbcc.kidly.boundedcontext.childprofile.app.outbox;

import com.bbcc.kidly.boundedcontext.childprofile.app.port.outbox.ChildProfileOutboxSender;
import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxMessage;
import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxRepository;
import com.bbcc.kidly.member.support.outbox.OutboxRelay;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
public class ChildProfileOutboxRelay extends OutboxRelay<ChildProfileOutboxMessage> {

    public ChildProfileOutboxRelay(ChildProfileOutboxRepository repository, ChildProfileOutboxSender sender,
        TransactionTemplate transactionTemplate) {
        super("자녀 프로필", "childprofile-outbox-dispatcher", repository, sender, transactionTemplate);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(ChildProfileOutboxMessage message) {
        enqueue(message);
    }
}
