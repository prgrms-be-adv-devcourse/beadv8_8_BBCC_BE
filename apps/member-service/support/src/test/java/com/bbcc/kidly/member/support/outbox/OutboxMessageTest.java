package com.bbcc.kidly.member.support.outbox;

import static com.bbcc.kidly.member.support.outbox.TestOutboxMessage.EVENT_ID;
import static com.bbcc.kidly.member.support.outbox.TestOutboxMessage.PAYLOAD;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OutboxMessageTest {

    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 10, 30, 12, 0);

    @Test
    @DisplayName("새로 만든 메시지는 받은 값을 담고, 보내지 않은 상태에서 시도 횟수 0으로 시작한다")
    void createUnsent() {
        var message = TestOutboxMessage.withdrawn();

        assertThat(message)
            .extracting(
                OutboxMessage::getEventId,
                OutboxMessage::getEventType,
                OutboxMessage::getTopic,
                OutboxMessage::getMessageKey,
                OutboxMessage::getPayload,
                OutboxMessage::getAttemptCount,
                OutboxMessage::getSentAt,
                OutboxMessage::getLastError)
            .containsExactly(EVENT_ID, "member.withdrawn", "kidly.member.events", "1", PAYLOAD, 0, null, null);
    }

    @Test
    @DisplayName("보냈다고 기록하면 시도 횟수가 1 늘고 보낸 시각이 남는다")
    void markSent() {
        var message = TestOutboxMessage.withdrawn();

        message.markSent(SENT_AT);

        assertThat(message)
            .extracting(OutboxMessage::getAttemptCount, OutboxMessage::getSentAt)
            .containsExactly(1, SENT_AT);
    }

    @Test
    @DisplayName("보내지 못했다고 기록하면 시도 횟수가 1 늘고 마지막 오류가 남으며, 보내지 않은 상태 그대로다")
    void recordFailure() {
        var message = TestOutboxMessage.withdrawn();

        message.recordFailure("Topic kidly.member.events not present in metadata after 3000 ms.");

        assertThat(message)
            .extracting(OutboxMessage::getAttemptCount, OutboxMessage::getLastError, OutboxMessage::getSentAt)
            .containsExactly(1, "Topic kidly.member.events not present in metadata after 3000 ms.", null);
    }

    @ParameterizedTest(name = "오류 {0}자 → {1}자")
    @CsvSource({"500, 500", "501, 500", "510, 500"})
    @DisplayName("오류는 칸 길이(500자)까지 그대로 남기고, 넘으면 앞 500자만 남긴다")
    void recordFailureKeepsErrorWithinColumnLength(int errorLength, int keptLength) {
        var message = TestOutboxMessage.withdrawn();

        message.recordFailure("가".repeat(errorLength));

        assertThat(message.getLastError()).isEqualTo("가".repeat(keptLength));
    }
}
