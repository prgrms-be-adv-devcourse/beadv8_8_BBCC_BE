package com.bbcc.kidly.boundedcontext.auth.app;

import com.bbcc.kidly.boundedcontext.auth.app.port.AuthEventSender;
import com.bbcc.kidly.shared.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 인증이 발행한 이벤트(토큰 무효화)를 커밋 뒤 Kafka로 보낸다.
 * 롤백되거나 트랜잭션 밖에서 발행하면 보내지 않는다(@DomainEventListener와 같은 조건). 새 트랜잭션은 필요 없다.
 * 같은 커밋 뒤에 도는 다른 리스너(@DomainEventListener, 순서 LOWEST_PRECEDENCE)보다 먼저 전송 대기열에 넣는다.
 * 실제 전송은 AuthEventSender가 다른 스레드에서 하므로, 브로커가 죽어 있어도 뒤의 리스너를 늦추지 않는다.
 * 잃으면 무효화된 토큰이 만료(최대 30분)까지 통과하지만 그 뒤로는 피해가 없어 아웃박스를 쓰지 않는다.
 * kidly.kafka.producer.enabled가 false면 만들지 않는다(Kafka 없이 도는 테스트). 값이 없으면 보낸다.
 */
@Component
@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AuthEventRelay {

    private static final String PRODUCER = "auth";

    private final AuthEventSender authEventSender;

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relay(DomainEvent event) {
        // 한 앱에 도메인이 여럿이라 인증이 발행한 이벤트만 보낸다
        if (!PRODUCER.equals(event.producer())) {
            return;
        }
        authEventSender.send(event);
    }
}
