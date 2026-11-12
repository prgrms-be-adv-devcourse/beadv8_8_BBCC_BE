package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.replica.ReplicaService;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.ChildAccountChangedCommand;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * child-account.changed를 받아 자녀 계정 복사본 반영을 맡긴다(#906). 계정이 삭제 상태가 되면(#1001) 받은 이벤트를 앱 안 Spring
 * 이벤트로 다시 알려 조르기가 진행 중 조르기를 철회하게 하고(아웃박스 · Kafka로 나가지 않는다) 그다음 그 프로필 장바구니를 비운다(#1207 순서). 이벤트 필드가 바뀌면 여기 변환만 고친다.
 * 받는 곳: Kafka 리스너({@link AuthEventKafkaListener})뿐이다. 처리 메서드는 트랜잭션을 연다.
 */
@Component
@RequiredArgsConstructor
public class ChildAccountEventListener {

    private final ReplicaService replicaService;
    private final EventPublisher eventPublisher;

    /**
     * 계정 ID · 발생 시각 · 프로필 ID · 보호자 ID 중 하나라도 없으면 무시한다(기존 복사본을 덮지 않음).
     */
    @Transactional
    public void onChildAccountChanged(ChildAccountChangedEvent event) {
        Long accountId = event.data() == null ? null : event.data().accountId();
        if (!EventGuard.accept("자녀 계정", event, "accountId", accountId, missingValue(event))) {
            return;
        }
        ChildAccountChangedEvent.Data data = event.data();
        if (replicaService.applyChildAccountChanged(new ChildAccountChangedCommand(
            data.accountId(), data.childProfileId(), data.guardianId(), data.deleted(), event.occurredAt()))) {
            // 삭제 상태면(#1001) 조르기 철회를 앱 안 Spring 이벤트로 먼저 알리고(아웃박스 아님, 조르기 예약을 잠그고 푼다) 그다음 장바구니를
            // 비운다(#1207: 조르기 예약이 먼저 잠기고 풀려 장바구니 비우기가 잠글 예약이 거의 없다)
            eventPublisher.publish(event);
            replicaService.clearDeletedAccountCart(data.accountId());
        }
    }

    private static String missingValue(ChildAccountChangedEvent event) {
        ChildAccountChangedEvent.Data data = event.data();
        if (data == null) {
            return "missing-data";
        }
        if (data.accountId() == null) {
            return "missing-account-id";
        }
        if (event.occurredAt() == null) {
            return "missing-occurred-at";
        }
        if (data.childProfileId() == null) {
            return "missing-child-profile-id";
        }
        if (data.guardianId() == null) {
            return "missing-guardian-id";
        }
        return null;
    }
}
