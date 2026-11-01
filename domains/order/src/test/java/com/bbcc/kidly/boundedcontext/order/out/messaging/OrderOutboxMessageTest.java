package com.bbcc.kidly.boundedcontext.order.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderOutboxMessageTest {

    private static final UUID EVENT_ID = UUID.fromString("6f1c2e4a-0b7d-4c1e-9a55-2d3f8e7b1a90");
    private static final LocalDateTime NOW = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    private static final String PAYLOAD = "{\"eventId\":\"6f1c2e4a-0b7d-4c1e-9a55-2d3f8e7b1a90\"}";

    @Test
    @DisplayName("만들면 이벤트 ID · 종류 · 토픽 · 키 · 본문을 그대로 담고, 보내지 않은 상태 · 시도 0 · 오류 없음")
    void createsUnsent() {
        OrderOutboxMessage message = create();

        assertThat(message.getEventId()).isEqualTo(EVENT_ID);
        assertThat(message.getEventType()).isEqualTo("order.created");
        assertThat(message.getTopic()).isEqualTo("kidly.order.events");
        assertThat(message.getMessageKey()).isEqualTo("1024");
        assertThat(message.getPayload()).isEqualTo(PAYLOAD);
        assertThat(message.getSentAt()).isNull();
        assertThat(message.getAttemptCount()).isZero();
        assertThat(message.getLastError()).isNull();
    }

    @Nested
    @DisplayName("보냄")
    class MarkSent {

        @Test
        @DisplayName("보낸 시각을 기록하고 시도를 1 늘린다")
        void recordsSentAt() {
            OrderOutboxMessage message = create();

            message.markSent(NOW);

            assertThat(message.getSentAt()).isEqualTo(NOW);
            assertThat(message.getAttemptCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 보낸 메시지를 다시 보냈다고 해도 처음 보낸 시각 · 시도 횟수 그대로(한 번만 처리)")
        void keepsFirstSent() {
            OrderOutboxMessage message = create();
            message.markSent(NOW);

            message.markSent(NOW.plusSeconds(1));

            assertThat(message.getSentAt()).isEqualTo(NOW);
            assertThat(message.getAttemptCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("보내기 실패")
    class RecordFailure {

        @Test
        @DisplayName("시도를 1 늘리고 마지막 오류를 바꾸며, 보내지 않은 상태로 남는다")
        void countsAttempt() {
            OrderOutboxMessage message = create();
            message.recordFailure("TimeoutException: first");

            message.recordFailure("TimeoutException: second");

            assertThat(message.getAttemptCount()).isEqualTo(2);
            assertThat(message.getLastError()).isEqualTo("TimeoutException: second");
            assertThat(message.getSentAt()).isNull();
        }

        @Test
        @DisplayName("오류가 칸 길이(500자)보다 길면 앞 500자만 남긴다")
        void truncatesLongError() {
            OrderOutboxMessage message = create();

            message.recordFailure("e".repeat(501));

            assertThat(message.getLastError()).isEqualTo("e".repeat(500));
        }

        @Test
        @DisplayName("보낸 뒤 성공하면 마지막 오류는 남아 있고 시도는 실패를 포함해 센다")
        void sentAfterFailure() {
            OrderOutboxMessage message = create();
            message.recordFailure("TimeoutException: first");

            message.markSent(NOW);

            assertThat(message.getAttemptCount()).isEqualTo(2);
            assertThat(message.getLastError()).isEqualTo("TimeoutException: first");
            assertThat(message.getSentAt()).isEqualTo(NOW);
        }
    }

    private static OrderOutboxMessage create() {
        return OrderOutboxMessage.create(EVENT_ID, "order.created", "kidly.order.events", "1024", PAYLOAD);
    }
}
