package com.bbcc.kidly.boundedcontext.auth;

import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxMessage;
import java.util.UUID;

public final class AuthOutboxMessageFixture {

    public static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public static final String PAYLOAD =
        "{\"eventId\":\"00000000-0000-0000-0000-000000000001\",\"data\":{\"accountId\":1}}";

    private AuthOutboxMessageFixture() {
    }

    public static AuthOutboxMessage childAccountChanged() {
        return childAccountChanged(EVENT_ID);
    }

    public static AuthOutboxMessage childAccountChanged(UUID eventId) {
        return AuthOutboxMessage.create(eventId, "child-account.changed", "1", PAYLOAD);
    }
}
