package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductOutboxMessageFixture.EVENT_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOutboxMessageFixture.PAYLOAD;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductOutboxMessageTest {

    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 10, 29, 12, 0);

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

    @Test
    @DisplayName("보냈다고 기록하면 시도 횟수가 1 늘고 보낸 시각이 남는다")
    void markSent() {
        ProductOutboxMessage message = ProductOutboxMessage.create(ProductOutboxMessageFixture.productChanged());

        message.markSent(SENT_AT);

        assertThat(message)
            .extracting(ProductOutboxMessage::getAttemptCount, ProductOutboxMessage::getSentAt)
            .containsExactly(1, SENT_AT);
    }

    @Test
    @DisplayName("이미 보낸 메시지를 다시 보냈다고 기록해도 시도 횟수와 보낸 시각은 처음 값 그대로다")
    void markSentOnce() {
        ProductOutboxMessage message = ProductOutboxMessage.create(ProductOutboxMessageFixture.productChanged());
        message.markSent(SENT_AT);

        message.markSent(SENT_AT.plusSeconds(1));

        assertThat(message)
            .extracting(ProductOutboxMessage::getAttemptCount, ProductOutboxMessage::getSentAt)
            .containsExactly(1, SENT_AT);
    }
}
