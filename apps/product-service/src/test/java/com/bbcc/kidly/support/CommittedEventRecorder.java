package com.bbcc.kidly.support;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * API 요청은 서버 스레드에서 처리되어 @RecordApplicationEvents가 놓치므로 이 컴포넌트로 기록한다.
 */
@Component
public class CommittedEventRecorder {

    private final List<DomainEvent> events = new CopyOnWriteArrayList<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void record(DomainEvent event) {
        events.add(event);
    }

    public <T extends DomainEvent> List<T> events(Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    public void clear() {
        events.clear();
    }
}
