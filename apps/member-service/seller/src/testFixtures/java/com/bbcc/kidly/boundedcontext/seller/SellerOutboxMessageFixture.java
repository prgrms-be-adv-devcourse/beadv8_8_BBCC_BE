package com.bbcc.kidly.boundedcontext.seller;

import com.bbcc.kidly.member.support.outbox.OutboxMessage;
import java.util.UUID;

public final class SellerOutboxMessageFixture {

    public static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public static final String PAYLOAD =
        "{\"eventId\":\"00000000-0000-0000-0000-000000000001\",\"data\":{\"sellerId\":1}}";

    private SellerOutboxMessageFixture() {
    }

    public static OutboxMessage.CreateInfo changed() {
        return changed(EVENT_ID);
    }

    public static OutboxMessage.CreateInfo changed(UUID eventId) {
        return new OutboxMessage.CreateInfo(eventId, "seller.changed", "kidly.seller.events", "1", PAYLOAD);
    }
}
