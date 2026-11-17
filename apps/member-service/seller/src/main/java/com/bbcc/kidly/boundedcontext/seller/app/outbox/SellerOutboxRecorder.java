package com.bbcc.kidly.boundedcontext.seller.app.outbox;

import com.bbcc.kidly.boundedcontext.seller.app.port.outbox.SellerEventOutbox;
import com.bbcc.kidly.shared.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 업무 트랜잭션 안에서 저장하므로 업무가 롤백되면 메시지도 없고, 저장이 실패하면 업무도 커밋되지 않는다.
 * 트랜잭션 밖에서 발행한 이벤트는 이 리스너가 불리지 않는다.
 * Kafka 보내기를 꺼도(kidly.kafka.producer.enabled=false) 저장은 한다.
 */
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
