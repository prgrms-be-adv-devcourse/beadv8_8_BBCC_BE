package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentEventSender;
import com.bbcc.kidly.shared.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 결제가 발행한 이벤트를 커밋 뒤 Kafka로도 보낸다. Spring 이벤트로 받는 쪽(주문 · 정산)은 그대로다.
 * 롤백되거나 트랜잭션 밖에서 발행하면 보내지 않는다(@DomainEventListener와 같은 조건). 새 트랜잭션은 필요 없다.
 * 같은 커밋 뒤에 도는 다른 리스너보다 먼저 보낸다.
 */
@Component
@RequiredArgsConstructor
public class PaymentEventRelay {

    private static final String PRODUCER = "payment";

    private final PaymentEventSender paymentEventSender;

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relay(DomainEvent event) {
        // 한 앱에 도메인이 여럿이라 결제가 발행한 이벤트만 보낸다
        if (!PRODUCER.equals(event.producer())) {
            return;
        }
        paymentEventSender.send(event);
    }
}
