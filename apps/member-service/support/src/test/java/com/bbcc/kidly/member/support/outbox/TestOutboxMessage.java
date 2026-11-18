package com.bbcc.kidly.member.support.outbox;

import java.util.UUID;

class TestOutboxMessage extends OutboxMessage {

    static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final String PAYLOAD = "{\"eventId\":\"00000000-0000-0000-0000-000000000001\",\"data\":{\"memberId\":1}}";

    TestOutboxMessage(CreateInfo info) {
        super(info);
    }

    static TestOutboxMessage withdrawn() {
        return withdrawn(EVENT_ID);
    }

    static TestOutboxMessage withdrawn(UUID eventId) {
        return new TestOutboxMessage(new CreateInfo(eventId, "member.withdrawn", "kidly.member.events", "1", PAYLOAD));
    }
}
