package com.bbcc.kidly.boundedcontext.seller.app.outbox;

import com.bbcc.kidly.boundedcontext.seller.app.port.outbox.SellerOutboxSender;
import com.bbcc.kidly.boundedcontext.seller.domain.outbox.SellerOutboxMessage;
import com.bbcc.kidly.boundedcontext.seller.domain.outbox.SellerOutboxRepository;
import com.bbcc.kidly.member.support.outbox.OutboxRelay;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
public class SellerOutboxRelay extends OutboxRelay<SellerOutboxMessage> {

    public SellerOutboxRelay(SellerOutboxRepository repository, SellerOutboxSender sender,
        TransactionTemplate transactionTemplate) {
        super("판매자", "seller-outbox-dispatcher", repository, sender, transactionTemplate);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(SellerOutboxMessage message) {
        enqueue(message);
    }
}
