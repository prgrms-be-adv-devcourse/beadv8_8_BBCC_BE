package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.port.MemberEventSender;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 회원이 발행한 가입 · 변경 이벤트를 커밋 뒤 Kafka로도 보낸다. 탈퇴는 아웃박스로 보낸다.
 * 롤백되거나 트랜잭션 밖에서 발행하면 보내지 않는다(@DomainEventListener와 같은 조건). 새 트랜잭션은 필요 없다.
 * 같은 커밋 뒤에 도는 다른 리스너(@DomainEventListener, 순서 LOWEST_PRECEDENCE)보다 먼저 전송 대기열에 넣는다.
 * 실제 전송은 MemberEventSender가 다른 스레드에서 하므로, 브로커가 죽어 있어도 뒤의 리스너를 늦추지 않는다.
 * kidly.kafka.producer.enabled가 false면 만들지 않는다(Kafka 없이 도는 테스트). 값이 없으면 보낸다.
 */
@Component
@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MemberEventRelay {

    private static final String PRODUCER = "member";

    private final MemberEventSender memberEventSender;

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relay(DomainEvent event) {
        // 한 앱에 도메인이 여럿이라 회원이 발행한 이벤트만 보낸다. 탈퇴는 아웃박스 릴레이가 보낸다(MemberOutboxRecorder)
        if (!PRODUCER.equals(event.producer()) || event instanceof MemberWithdrawnEvent) {
            return;
        }
        memberEventSender.send(event);
    }
}
