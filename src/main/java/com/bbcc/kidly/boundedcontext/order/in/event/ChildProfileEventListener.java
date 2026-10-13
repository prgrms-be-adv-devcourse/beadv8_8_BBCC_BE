package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.replica.ReplicaService;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.ChildChangedCommand;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * child.changed를 받아 복사본 반영을 맡긴다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 */
@Component
@RequiredArgsConstructor
public class ChildProfileEventListener {

    private final ReplicaService replicaService;

    @DomainEventListener
    public void onChildChanged(ChildChangedEvent event) {
        replicaService.applyChildChanged(toCommand(event));
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
