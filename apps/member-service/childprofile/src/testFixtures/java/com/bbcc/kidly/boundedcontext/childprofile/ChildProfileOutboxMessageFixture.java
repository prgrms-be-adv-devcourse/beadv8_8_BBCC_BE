package com.bbcc.kidly.boundedcontext.childprofile;

import com.bbcc.kidly.member.support.outbox.OutboxMessage;
import java.util.UUID;

public final class ChildProfileOutboxMessageFixture {

    public static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public static final String PAYLOAD =
        "{\"eventId\":\"00000000-0000-0000-0000-000000000001\",\"data\":{\"childId\":3,\"heightCm\":115.0}}";

    private ChildProfileOutboxMessageFixture() {
    }

    public static OutboxMessage.CreateInfo childChanged() {
        return childChanged(EVENT_ID);
    }

    public static OutboxMessage.CreateInfo childChanged(UUID eventId) {
        return new OutboxMessage.CreateInfo(
            eventId, "child.changed", "kidly.childprofile.events", "3", PAYLOAD);
    }
}
