package com.bbcc.kidly.boundedcontext.order;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 흐름 테스트용: 커밋된 도메인 이벤트를 모은다. API 요청은 서버 스레드에서 처리되어 @RecordApplicationEvents가
 * 놓치므로 이 컴포넌트로 기록한다. 기록 외 동작은 없다(다시 발행하지 않음) — 다른 테스트에 영향 없다.
 * 테스트 클래스패스의 컴포넌트 스캔에 잡혀 모든 통합 · 기능 테스트 컨텍스트에 똑같이 등록된다.
 */
@Component
public class OrderFlowEventRecorder {

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
