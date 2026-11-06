package com.bbcc.kidly.boundedcontext.childprofile.app.outbox;

import com.bbcc.kidly.boundedcontext.childprofile.app.port.outbox.ChildProfileEventOutbox;
import com.bbcc.kidly.shared.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 트랜잭션 밖에서 발행한 이벤트는 이 리스너가 불리지 않는다(@DomainEventListener와 같은 조건).
 * 다른 커밋 직전 리스너나 커밋 때의 flush 중에 발행한 이벤트도 저장되지 않는다. 커밋 직전 단계는 시작할 때 등록돼 있던 리스너만 부르기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class ChildProfileOutboxRecorder {

    private static final String PRODUCER = "childprofile";

    private final ChildProfileEventOutbox childProfileEventOutbox;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void record(DomainEvent event) {
        if (!PRODUCER.equals(event.producer())) {
            return;
        }
        childProfileEventOutbox.append(event);
    }
}
