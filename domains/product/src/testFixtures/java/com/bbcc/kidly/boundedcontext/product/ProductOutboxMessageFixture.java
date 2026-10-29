package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;
import java.util.UUID;

final class ProductOutboxMessageFixture {

    static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    static final String PAYLOAD = "{\"eventId\":\"00000000-0000-0000-0000-000000000001\",\"data\":{\"productId\":1}}";

    private ProductOutboxMessageFixture() {
    }

    /**
     * 상품 1의 상품 정보 변경됨을 kidly.product.events로 보낼 메시지다. 키는 상품 ID다.
     */
    static ProductOutboxMessage.CreateInfo productChanged() {
        return new ProductOutboxMessage.CreateInfo(EVENT_ID, "product.changed", "kidly.product.events", "1", PAYLOAD);
    }
}
