package com.bbcc.kidly.boundedcontext.member.app.outbox;

import com.bbcc.kidly.boundedcontext.member.app.port.outbox.MemberEventOutbox;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import com.bbcc.kidly.shared.member.event.MemberSuspendedEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 가입 · 정지 · 탈퇴는 잃으면 이메일 복사본이나 자녀 정보가 저절로 복구되지 않아 아웃박스에 저장한다.
 * 업무 트랜잭션 안에서 저장하므로 업무가 롤백되면 메시지도 없고, 저장이 실패하면 업무도 커밋되지 않는다.
 * 트랜잭션 밖에서 발행한 이벤트는 이 리스너가 불리지 않는다.
 * Kafka 보내기를 꺼도(kidly.kafka.producer.enabled=false) 저장은 한다. 보내는 건 릴레이 스케줄러다.
 */
@Component
@RequiredArgsConstructor
public class MemberOutboxRecorder {

    private static final Set<Class<? extends DomainEvent>> EVENT_TYPES = Set.of(
        MemberRegisteredEvent.class, MemberSuspendedEvent.class, MemberWithdrawnEvent.class);

    private final MemberEventOutbox memberEventOutbox;

    public static boolean records(DomainEvent event) {
        return EVENT_TYPES.contains(event.getClass());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void record(DomainEvent event) {
        if (records(event)) {
            memberEventOutbox.append(event);
        }
    }
}
