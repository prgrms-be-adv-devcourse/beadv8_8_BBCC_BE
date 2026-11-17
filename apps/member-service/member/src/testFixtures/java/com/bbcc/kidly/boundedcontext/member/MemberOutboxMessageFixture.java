package com.bbcc.kidly.boundedcontext.member;

import com.bbcc.kidly.boundedcontext.member.domain.outbox.MemberOutboxMessage;
import java.util.UUID;

public final class MemberOutboxMessageFixture {

    public static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public static final String PAYLOAD =
        "{\"eventId\":\"00000000-0000-0000-0000-000000000001\",\"data\":{\"memberId\":1}}";

    private MemberOutboxMessageFixture() {
    }

    public static MemberOutboxMessage.CreateInfo withdrawn() {
        return withdrawn(EVENT_ID);
    }

    public static MemberOutboxMessage.CreateInfo withdrawn(UUID eventId) {
        return new MemberOutboxMessage.CreateInfo(eventId, "member.withdrawn", "kidly.member.events", "1", PAYLOAD);
    }
}
