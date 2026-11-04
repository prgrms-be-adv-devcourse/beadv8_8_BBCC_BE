package com.bbcc.kidly.boundedcontext.childprofile;

import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxMessage;
import java.util.UUID;

public final class ChildProfileOutboxMessageFixture {

    public static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    // 로그에 남지 않아야 하는 아이의 키를 넣어 둔다
    public static final String PAYLOAD =
        "{\"eventId\":\"00000000-0000-0000-0000-000000000001\",\"data\":{\"childId\":3,\"heightCm\":115.0}}";

    private ChildProfileOutboxMessageFixture() {
    }

    /**
     * 자녀 3의 자녀 정보 변경됨을 kidly.childprofile.events로 보낼 메시지다. 키는 자녀 ID다.
     */
    public static ChildProfileOutboxMessage.CreateInfo childChanged() {
        return childChanged(EVENT_ID);
    }

    /**
     * 이벤트 ID만 바꾼 자녀 3의 메시지다. 여러 메시지를 저장할 때 event_id UNIQUE에 걸리지 않게 쓴다.
     */
    public static ChildProfileOutboxMessage.CreateInfo childChanged(UUID eventId) {
        return new ChildProfileOutboxMessage.CreateInfo(
            eventId, "child.changed", "kidly.childprofile.events", "3", PAYLOAD);
    }
}
