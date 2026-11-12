package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.replica.ReplicaService;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.ChildChangedCommand;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * child.changed를 받아 복사본 반영을 맡긴다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 * 받는 곳(#541): Kafka 리스너({@link ChildProfileEventKafkaListener}), 스위치가 꺼졌을 때 Spring 수신({@link SpringEventReceiver}).
 * 처리 메서드는 부르는 쪽 트랜잭션에 합류하고(Spring 수신), 없으면 새로 연다(Kafka 리스너).
 * 리스너는 재시도가 없어 저장이 실패하면 이벤트가 사라지므로, 깨진 값은 여기서 걸러 예외 없이 처리한다.
 * 자녀가 삭제 상태가 되면(#1001) 복사본 반영과 같은 트랜잭션에서 받은 이벤트를 앱 안 Spring 이벤트로 그대로 다시 알리고(EventPublisher,
 * 아웃박스 · Kafka로 나가지 않는다) 그다음 그 자녀 장바구니를 비운다(ReplicaService, #1207 순서). 조르기가 이것을 받아 진행 중 조르기를 철회한다
 * (주문은 조르기를 모르므로 컨텍스트 규칙상 직접 부르지 않는다). 같은 트랜잭션이라 조르기 처리가 실패하면 함께 롤백되고 재시도된다.
 */
@Component
@RequiredArgsConstructor
public class ChildProfileEventListener {

    private final ReplicaService replicaService;
    private final EventPublisher eventPublisher;

    /**
     * 자녀 ID · 회원 ID · 발생 시각이 없으면 무시한다. 키 · 몸무게 · 성별 · 생년월일이 없으면 무시하되(#251 확정 기준,
     * 기존 복사본을 지우거나 덮지 않음), 삭제 이벤트는 삭제가 빠지면 지워진 자녀로 계속 주문할 수 있으므로
     * 복사본이 없어도 삭제 사실을 보존한다. 체형 · 선호 핏은 계약상 비어도 되므로 그대로 반영한다.
     */
    @Transactional
    public void onChildChanged(ChildChangedEvent event) {
        Long childId = event.data() == null ? null : event.data().childId();
        if (!EventGuard.accept("자녀", event, "childId", childId, missingIdentity(event))) {
            return;
        }
        ChildChangedEvent.Data data = event.data();
        String missingBody = missingProfile(data);
        // 삭제면 조르기 철회(앱 안 Spring 이벤트, 조르기 예약을 잠그고 푼다)를 먼저 알리고 그다음 장바구니를 비운다(#1207): 조르기 예약이
        // 먼저 잠기고 풀려 장바구니 비우기가 잠글 예약이 거의 없다(예약 잠금을 두 번 나눠 잡는 일을 줄인다)
        if (missingBody != null && data.deleted()) {
            if (replicaService.applyChildDeleted(data.childId(), data.memberId(), event.occurredAt())) {
                eventPublisher.publish(event);
            }
            replicaService.clearDeletedChildCart(data.childId());
            return;
        }
        if (EventGuard.accept("자녀", event, "childId", childId, missingBody)) {
            if (replicaService.applyChildChanged(toCommand(event)) && data.deleted()) {
                eventPublisher.publish(event);
            }
            replicaService.clearDeletedChildCart(data.childId());
        }
    }

    private static String missingIdentity(ChildChangedEvent event) {
        ChildChangedEvent.Data data = event.data();
        if (data == null) {
            return "missing-data";
        }
        if (data.childId() == null) {
            return "missing-child-id";
        }
        if (data.memberId() == null) {
            return "missing-member-id";
        }
        if (event.occurredAt() == null) {
            return "missing-occurred-at";
        }
        return null;
    }

    private static String missingProfile(ChildChangedEvent.Data data) {
        if (data.heightCm() == null) {
            return "missing-height";
        }
        if (data.weightKg() == null) {
            return "missing-weight";
        }
        if (data.gender() == null) {
            return "missing-gender";
        }
        if (data.birthDate() == null) {
            return "missing-birth-date";
        }
        return null;
    }

    /**
     * 이름은 이벤트에 아직 없어서 null, 체형은 enum 이름(입력하지 않았으면 null)으로 옮긴다.
     */
    private ChildChangedCommand toCommand(ChildChangedEvent event) {
        ChildChangedEvent.Data data = event.data();
        return new ChildChangedCommand(
            data.childId(),
            data.memberId(),
            null,
            data.heightCm(),
            data.weightKg(),
            data.bodyType() == null ? null : data.bodyType().name(),
            data.deleted(),
            event.occurredAt());
    }
}
