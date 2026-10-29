package com.bbcc.kidly.boundedcontext.seller.app;

import com.bbcc.kidly.boundedcontext.seller.app.port.SellerEventSender;
import com.bbcc.kidly.shared.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 판매자가 발행한 이벤트를 커밋 뒤 Kafka로도 보낸다. Spring 이벤트로 받는 쪽(회원 · 주문 · 상품 · 추천)은 그대로다.
 * 롤백되거나 트랜잭션 밖에서 발행하면 보내지 않는다(@DomainEventListener와 같은 조건). 새 트랜잭션은 필요 없다.
 * 같은 커밋 뒤에 도는 다른 리스너(@DomainEventListener, 순서 LOWEST_PRECEDENCE)보다 먼저 전송 대기열에 넣는다.
 * 실제 전송은 SellerEventSender가 다른 스레드에서 하므로, 브로커가 죽어 있어도 뒤의 리스너를 늦추지 않는다.
 * kidly.kafka.producer.enabled가 false면 만들지 않는다(Kafka 없이 도는 테스트). 값이 없으면 보낸다.
 */
@Component
@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SellerEventRelay {

    private static final String PRODUCER = "seller";

    private final SellerEventSender sellerEventSender;

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relay(DomainEvent event) {
        // 한 앱에 도메인이 여럿이라 판매자가 발행한 이벤트만 보낸다
        if (!PRODUCER.equals(event.producer())) {
            return;
        }
        sellerEventSender.send(event);
    }
}
