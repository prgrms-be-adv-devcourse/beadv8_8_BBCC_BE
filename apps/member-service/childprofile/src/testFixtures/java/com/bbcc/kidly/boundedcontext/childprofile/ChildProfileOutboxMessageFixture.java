package com.bbcc.kidly.boundedcontext.childprofile;

import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxMessage;
import java.util.UUID;

public final class ChildProfileOutboxMessageFixture {

    public static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public static final String PAYLOAD =
        "{\"eventId\":\"00000000-0000-0000-0000-000000000001\",\"data\":{\"childId\":3,\"heightCm\":115.0}}";

    private ChildProfileOutboxMessageFixture() {
    }

    public static ChildProfileOutboxMessage.CreateInfo childChanged() {
        return childChanged(EVENT_ID);
    }

    public static ChildProfileOutboxMessage.CreateInfo childChanged(UUID eventId) {
        return new ChildProfileOutboxMessage.CreateInfo(
            eventId, "child.changed", "kidly.childprofile.events", "3", PAYLOAD);
    }
}
