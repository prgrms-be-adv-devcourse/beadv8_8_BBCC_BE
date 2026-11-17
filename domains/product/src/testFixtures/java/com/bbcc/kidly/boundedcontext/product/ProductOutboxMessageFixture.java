package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;
import java.util.UUID;

final class ProductOutboxMessageFixture {

    static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    static final String PAYLOAD = "{\"eventId\":\"00000000-0000-0000-0000-000000000001\",\"data\":{\"productId\":1}}";

    private ProductOutboxMessageFixture() {
    }

    static ProductOutboxMessage.CreateInfo productChanged() {
        return productChanged(EVENT_ID);
    }

    static ProductOutboxMessage.CreateInfo productChanged(UUID eventId) {
        return new ProductOutboxMessage.CreateInfo(eventId, "product.changed", "kidly.product.events", "1", PAYLOAD);
    }
}
