package com.bbcc.kidly.boundedcontext.product.app.outbox;

import com.bbcc.kidly.boundedcontext.product.app.port.outbox.ProductEventOutbox;
import com.bbcc.kidly.shared.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 상품이 발행한 이벤트를 커밋 직전에 같은 트랜잭션 안에서 아웃박스에 저장한다.
 * 업무가 롤백되면 메시지도 남지 않고, 저장이 실패하면 업무도 커밋되지 않는다.
 * 한 앱에 도메인이 여럿이라 상품이 발행한 이벤트만 저장한다.
 * 트랜잭션 밖에서 발행한 이벤트는 이 리스너가 불리지 않는다(@DomainEventListener와 같은 조건).
 */
@Component
@RequiredArgsConstructor
public class ProductOutboxRecorder {

    private static final String PRODUCER = "product";

    private final ProductEventOutbox productEventOutbox;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void record(DomainEvent event) {
        if (!PRODUCER.equals(event.producer())) {
            return;
        }
        productEventOutbox.append(event);
    }
}
