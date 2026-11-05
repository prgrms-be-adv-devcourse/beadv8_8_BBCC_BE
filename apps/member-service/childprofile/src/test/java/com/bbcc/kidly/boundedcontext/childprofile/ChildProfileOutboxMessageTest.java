package com.bbcc.kidly.boundedcontext.childprofile;

import static com.bbcc.kidly.boundedcontext.childprofile.ChildProfileOutboxMessageFixture.EVENT_ID;
import static com.bbcc.kidly.boundedcontext.childprofile.ChildProfileOutboxMessageFixture.PAYLOAD;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxMessage;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChildProfileOutboxMessageTest {

    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 10, 30, 12, 0);

    @Test
    @DisplayName("새로 만든 메시지는 받은 값을 담고, 보내지 않은 상태에서 시도 횟수 0으로 시작한다")
    void createUnsent() {
        var message = ChildProfileOutboxMessage.create(ChildProfileOutboxMessageFixture.childChanged());

        assertThat(message)
            .extracting(
                ChildProfileOutboxMessage::getEventId,
                ChildProfileOutboxMessage::getEventType,
                ChildProfileOutboxMessage::getTopic,
                ChildProfileOutboxMessage::getMessageKey,
                ChildProfileOutboxMessage::getPayload,
                ChildProfileOutboxMessage::getAttemptCount,
                ChildProfileOutboxMessage::getSentAt,
                ChildProfileOutboxMessage::getLastError)
            .containsExactly(EVENT_ID, "child.changed", "kidly.childprofile.events", "3", PAYLOAD, 0, null, null);
    }

    @Test
    @DisplayName("보냈다고 기록하면 시도 횟수가 1 늘고 보낸 시각이 남는다")
    void markSent() {
        var message = ChildProfileOutboxMessage.create(ChildProfileOutboxMessageFixture.childChanged());

        message.markSent(SENT_AT);

        assertThat(message)
            .extracting(ChildProfileOutboxMessage::getAttemptCount, ChildProfileOutboxMessage::getSentAt)
            .containsExactly(1, SENT_AT);
    }

    @Test
    @DisplayName("보내지 못했다고 기록하면 시도 횟수가 1 늘고 마지막 오류가 남으며, 보내지 않은 상태 그대로다")
    void recordFailure() {
        var message = ChildProfileOutboxMessage.create(ChildProfileOutboxMessageFixture.childChanged());

        message.recordFailure("Topic kidly.childprofile.events not present in metadata after 3000 ms.");

        assertThat(message)
            .extracting(
                ChildProfileOutboxMessage::getAttemptCount,
                ChildProfileOutboxMessage::getLastError,
                ChildProfileOutboxMessage::getSentAt)
            .containsExactly(1, "Topic kidly.childprofile.events not present in metadata after 3000 ms.", null);
    }

    @ParameterizedTest(name = "오류 {0}자 → {1}자")
    @CsvSource({"500, 500", "501, 500", "510, 500"})
    @DisplayName("오류는 칸 길이(500자)까지 그대로 남기고, 넘으면 앞 500자만 남긴다")
    void recordFailureKeepsErrorWithinColumnLength(int errorLength, int keptLength) {
        var message = ChildProfileOutboxMessage.create(ChildProfileOutboxMessageFixture.childChanged());

        message.recordFailure("가".repeat(errorLength));

        assertThat(message.getLastError()).isEqualTo("가".repeat(keptLength));
    }
}
