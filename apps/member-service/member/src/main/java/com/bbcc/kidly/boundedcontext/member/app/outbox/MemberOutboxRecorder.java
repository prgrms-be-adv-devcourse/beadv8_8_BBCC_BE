package com.bbcc.kidly.boundedcontext.member.app.outbox;

import com.bbcc.kidly.boundedcontext.member.app.port.outbox.MemberEventOutbox;
import com.bbcc.kidly.shared.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class MemberOutboxRecorder {

    private static final String PRODUCER = "member";

    private final MemberEventOutbox memberEventOutbox;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void record(DomainEvent event) {
        if (PRODUCER.equals(event.producer())) {
            memberEventOutbox.append(event);
        }
    }
}
