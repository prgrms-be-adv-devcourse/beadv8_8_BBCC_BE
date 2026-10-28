package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.replica.ReplicaService;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.ChildChangedCommand;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * child.changed를 받아 복사본 반영을 맡긴다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 * 리스너는 재시도가 없어 저장이 실패하면 이벤트가 사라지므로, 깨진 값은 여기서 걸러 예외 없이 처리한다.
 */
@Component
@RequiredArgsConstructor
public class ChildProfileEventListener {

    private final ReplicaService replicaService;

    /**
     * 자녀 ID · 회원 ID · 발생 시각이 없으면 무시한다. 키 · 몸무게 · 성별 · 생년월일이 없으면 무시하되(#251 확정 기준,
     * 기존 복사본을 지우거나 덮지 않음), 삭제 이벤트는 삭제가 빠지면 지워진 자녀로 계속 주문할 수 있으므로
     * 기존 복사본에 삭제 표시만 맡긴다. 체형 · 선호 핏은 계약상 비어도 되므로 그대로 반영한다.
     */
    @DomainEventListener
    public void onChildChanged(ChildChangedEvent event) {
        Long childId = event.data() == null ? null : event.data().childId();
        if (!EventGuard.accept("자녀", event, "childId", childId, missingIdentity(event))) {
            return;
        }
        ChildChangedEvent.Data data = event.data();
        String missingBody = missingProfile(data);
        if (missingBody != null && data.deleted()) {
            replicaService.applyChildDeleted(data.childId(), event.occurredAt());
            return;
        }
        if (EventGuard.accept("자녀", event, "childId", childId, missingBody)) {
            replicaService.applyChildChanged(toCommand(event));
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
