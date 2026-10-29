package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductOutboxMessageFixture.EVENT_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOutboxMessageFixture.PAYLOAD;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductOutboxMessageTest {

    @Test
    @DisplayName("새로 만든 메시지는 받은 값을 담고, 보내지 않은 상태에서 시도 횟수 0으로 시작한다")
    void createUnsent() {
        ProductOutboxMessage message = ProductOutboxMessage.create(ProductOutboxMessageFixture.productChanged());

        assertThat(message)
            .extracting(
                ProductOutboxMessage::getEventId,
                ProductOutboxMessage::getEventType,
                ProductOutboxMessage::getTopic,
                ProductOutboxMessage::getMessageKey,
                ProductOutboxMessage::getPayload,
                ProductOutboxMessage::getAttemptCount,
                ProductOutboxMessage::getSentAt,
                ProductOutboxMessage::getLastError)
            .containsExactly(EVENT_ID, "product.changed", "kidly.product.events", "1", PAYLOAD, 0, null, null);
    }
}
