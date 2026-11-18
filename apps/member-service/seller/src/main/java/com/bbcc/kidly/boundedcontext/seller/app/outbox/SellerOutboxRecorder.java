package com.bbcc.kidly.boundedcontext.seller.app.outbox;

import com.bbcc.kidly.shared.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SellerOutboxRecorder {

    private static final String PRODUCER = "seller";

    private final SellerEventOutbox sellerEventOutbox;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void record(DomainEvent event) {
        if (PRODUCER.equals(event.producer())) {
            sellerEventOutbox.append(event);
        }
    }
}
