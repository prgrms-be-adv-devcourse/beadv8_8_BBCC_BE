package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductOutboxMessageFixture.EVENT_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOutboxMessageFixture.PAYLOAD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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

    @Test
    @DisplayName("보내지 못했다고 기록하면 시도 횟수가 1 늘고 마지막 오류가 남으며, 보내지 않은 상태 그대로다")
    void recordFailure() {
        ProductOutboxMessage message = ProductOutboxMessage.create(ProductOutboxMessageFixture.productChanged());

        message.recordFailure("TimeoutException: Topic kidly.product.events not present in metadata after 5000 ms.");

        assertThat(message)
            .extracting(
                ProductOutboxMessage::getAttemptCount,
                ProductOutboxMessage::getLastError,
                ProductOutboxMessage::getSentAt)
            .containsExactly(
                1, "TimeoutException: Topic kidly.product.events not present in metadata after 5000 ms.", null);
    }

    @Test
    @DisplayName("오류가 500자를 넘으면 칸 길이에 맞춰 앞 500자만 남긴다")
    void recordFailureTruncatesLongError() {
        ProductOutboxMessage message = ProductOutboxMessage.create(ProductOutboxMessageFixture.productChanged());
        String error = "가".repeat(500) + "나".repeat(10);

        message.recordFailure(error);

        assertThat(message.getLastError()).isEqualTo("가".repeat(500));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("createInfosWithoutRequiredValue")
    @DisplayName("필수 값이 없으면 메시지를 만들지 않는다")
    void rejectCreateWithoutRequiredValue(ProductOutboxMessage.CreateInfo info) {
        assertThatThrownBy(() -> ProductOutboxMessage.create(info))
            .isInstanceOf(NullPointerException.class);
    }

    static Stream<Named<ProductOutboxMessage.CreateInfo>> createInfosWithoutRequiredValue() {
        return Stream.of(
            Named.of("info 없음", null),
            Named.of("eventId 없음",
                new ProductOutboxMessage.CreateInfo(null, "product.changed", "kidly.product.events", "1", PAYLOAD)),
            Named.of("eventType 없음",
                new ProductOutboxMessage.CreateInfo(EVENT_ID, null, "kidly.product.events", "1", PAYLOAD)),
            Named.of("topic 없음",
                new ProductOutboxMessage.CreateInfo(EVENT_ID, "product.changed", null, "1", PAYLOAD)),
            Named.of("messageKey 없음",
                new ProductOutboxMessage.CreateInfo(EVENT_ID, "product.changed", "kidly.product.events", null, PAYLOAD)),
            Named.of("payload 없음",
                new ProductOutboxMessage.CreateInfo(EVENT_ID, "product.changed", "kidly.product.events", "1", null)));
    }
}
