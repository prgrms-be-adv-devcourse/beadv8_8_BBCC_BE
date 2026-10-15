package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.replica.ReplicaService;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.ChildChangedCommand;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * child.changed를 받아 복사본 반영을 맡긴다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 * 리스너는 재시도가 없어 저장이 실패하면 이벤트가 사라지므로, 깨진 값은 여기서 걸러 예외 없이 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChildProfileEventListener {

    private final ReplicaService replicaService;

    /**
     * 자녀 ID · 회원 ID · 발생 시각이 없으면 무시한다. 키 · 몸무게가 없으면 복사본 행을 만들 수 없어 무시하되,
     * 삭제 이벤트는 삭제가 빠지면 지워진 자녀로 계속 주문할 수 있으므로 기존 복사본에 삭제 표시만 맡긴다.
     */
    @DomainEventListener
    public void onChildChanged(ChildChangedEvent event) {
        String missing = missingIdentity(event);
        if (missing != null) {
            warnSkipped(event, missing);
            return;
        }
        ChildChangedEvent.Data data = event.data();
        String missingBody = data.heightCm() == null ? "missing-height"
            : data.weightKg() == null ? "missing-weight" : null;
        if (missingBody == null) {
            replicaService.applyChildChanged(toCommand(event));
        } else if (data.deleted()) {
            replicaService.applyChildDeleted(data.childId(), event.occurredAt());
        } else {
            warnSkipped(event, missingBody);
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

    private static void warnSkipped(ChildChangedEvent event, String reason) {
        Long childId = event.data() == null ? null : event.data().childId();
        log.warn("자녀 이벤트 무시: event=child.changed childId={} reason={}", childId, reason);
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
