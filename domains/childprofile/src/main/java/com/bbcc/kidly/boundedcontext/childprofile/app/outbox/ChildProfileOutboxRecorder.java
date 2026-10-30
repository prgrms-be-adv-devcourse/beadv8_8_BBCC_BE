package com.bbcc.kidly.boundedcontext.childprofile.app.outbox;

import com.bbcc.kidly.boundedcontext.childprofile.app.port.outbox.ChildProfileEventOutbox;
import com.bbcc.kidly.shared.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 자녀 프로필이 발행한 이벤트를 커밋 직전에 아웃박스에 저장한다. 발행 코드는 이 저장을 모른다.
 * 업무 트랜잭션 안에서 저장하므로 업무가 롤백되면 메시지도 없고, 저장이 실패하면 업무도 커밋되지 않는다.
 * Kafka로 회원 탈퇴를 받아 자녀를 지우는 리스너처럼 자기 트랜잭션에서 발행한 이벤트도 그 트랜잭션의 커밋 직전에 저장한다.
 * 트랜잭션 밖에서 발행한 이벤트는 이 리스너가 불리지 않는다(@DomainEventListener와 같은 조건).
 * 다른 커밋 직전 리스너나 커밋 때의 flush 중에 발행한 이벤트도 저장되지 않는다. 커밋 직전 단계는 시작할 때 등록돼 있던 리스너만 부르기 때문이다.
 * Kafka 보내기를 꺼도(kidly.kafka.producer.enabled=false) 저장은 한다. 보내는 건 릴레이 스케줄러다.
 */
@Component
@RequiredArgsConstructor
public class ChildProfileOutboxRecorder {

    private static final String PRODUCER = "childprofile";

    private final ChildProfileEventOutbox childProfileEventOutbox;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void record(DomainEvent event) {
        // 한 앱에 도메인이 여럿이라 자녀 프로필이 발행한 이벤트만 저장한다
        if (!PRODUCER.equals(event.producer())) {
            return;
        }
        childProfileEventOutbox.append(event);
    }
}
