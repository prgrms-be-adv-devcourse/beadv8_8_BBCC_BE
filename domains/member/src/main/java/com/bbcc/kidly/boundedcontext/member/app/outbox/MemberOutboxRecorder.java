package com.bbcc.kidly.boundedcontext.member.app.outbox;

import com.bbcc.kidly.boundedcontext.member.app.port.outbox.MemberEventOutbox;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 회원 탈퇴 이벤트를 커밋 직전에 아웃박스에 저장한다. 발행 코드는 이 저장을 모른다.
 * 탈퇴만 저장한다. 잃으면 자녀 프로필이 탈퇴 회원의 자녀 정보를 지우지 못하기 때문이다.
 * 가입 · 변경은 잃어도 다음 거래 · 변경 때 맞춰지므로 MemberEventRelay가 커밋 뒤 바로 보낸다.
 * 업무 트랜잭션 안에서 저장하므로 업무가 롤백되면 메시지도 없고, 저장이 실패하면 업무도 커밋되지 않는다.
 * 트랜잭션 밖에서 발행한 이벤트는 이 리스너가 불리지 않는다(@DomainEventListener와 같은 조건).
 * Kafka 보내기를 꺼도(kidly.kafka.producer.enabled=false) 저장은 한다. 보내는 건 릴레이 스케줄러다.
 */
@Component
@RequiredArgsConstructor
public class MemberOutboxRecorder {

    private final MemberEventOutbox memberEventOutbox;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void record(MemberWithdrawnEvent event) {
        memberEventOutbox.append(event);
    }
}
