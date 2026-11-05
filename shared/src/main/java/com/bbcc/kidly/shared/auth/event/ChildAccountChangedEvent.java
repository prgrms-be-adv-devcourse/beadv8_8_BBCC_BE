package com.bbcc.kidly.shared.auth.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 자녀 계정이 만들어지거나({@code deleted = false}) 지워졌다({@code deleted = true}). 자녀 프로필이 계정 ↔ 프로필 연결 복사본을 갱신한다.
 * 계정 ID는 다시 쓰지 않는다. 같은 프로필에 계정을 다시 만들면 새 {@code accountId}로 온다.
 * 받는 쪽은 계정마다 "있음 → 삭제됨" 한 방향으로만 반영한다. 그러면 같은 메시지를 다시 받거나 계정 사이의 순서가 바뀌어도 결과가 같다.
 */
public record ChildAccountChangedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    public record Data(Long accountId, Long childProfileId, Long guardianId, boolean deleted) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.accountId());
    }

    @Override
    public String eventType() {
        return "child-account.changed";
    }
}
