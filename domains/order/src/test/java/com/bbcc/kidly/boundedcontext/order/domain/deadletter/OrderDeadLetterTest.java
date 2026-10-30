package com.bbcc.kidly.boundedcontext.order.domain.deadletter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderDeadLetterTest {

    private static final LocalDateTime RECEIVED_AT = LocalDateTime.of(2026, 10, 31, 9, 0);
    private static final LocalDateTime NOW = RECEIVED_AT.plusHours(1);
    private static final Long ADMIN_ID = 99L;

    @Test
    @DisplayName("처음 받은 행: OPEN · 받은 횟수 1 · 처음 = 마지막 받은 시각 · 처리 칸 비어 있음(저장소 upsert의 처음 값과 같다)")
    void receivedIsOpen() {
        OrderDeadLetter letter = OrderDeadLetter.received(received());

        assertThat(letter.getStatus()).isEqualTo(OrderDeadLetter.Status.OPEN);
        assertThat(letter.getReceivedCount()).isEqualTo(1);
        assertThat(letter.getFirstReceivedAt()).isEqualTo(RECEIVED_AT);
        assertThat(letter.getLastReceivedAt()).isEqualTo(RECEIVED_AT);
        assertThat(letter.getSourceOffset()).isEqualTo(7L);
        assertThat(letter.getFailureType()).isEqualTo(OrderDeadLetter.FailureType.STORE_FAILED);
        assertThat(letter.getReprocessCount()).isZero();
        assertThat(letter.getHandledBy()).isNull();
    }

    @Test
    @DisplayName("재처리 성공: REPROCESSED · 처리자 · 처리 시각, 재처리 횟수 +1 · 마지막 재처리 시각, 마지막 실패 이유는 지운다")
    void reprocessed() {
        OrderDeadLetter letter = OrderDeadLetter.received(received());
        letter.recordReprocessFailure("InvalidFormatException", RECEIVED_AT.plusMinutes(5));

        letter.markReprocessed(ADMIN_ID, NOW);

        assertThat(letter.getStatus()).isEqualTo(OrderDeadLetter.Status.REPROCESSED);
        assertThat(letter.getHandledBy()).isEqualTo(ADMIN_ID);
        assertThat(letter.getHandledAt()).isEqualTo(NOW);
        assertThat(letter.getReprocessCount()).isEqualTo(2);
        assertThat(letter.getLastReprocessedAt()).isEqualTo(NOW);
        assertThat(letter.getLastReprocessResult()).isNull();
    }

    @Test
    @DisplayName("재처리 실패: OPEN 그대로, 재처리 횟수 +1 · 마지막 재처리 시각 · 실패 이유(500자까지), 처리자는 비어 있다")
    void reprocessFailed() {
        OrderDeadLetter letter = OrderDeadLetter.received(received());

        letter.recordReprocessFailure("x".repeat(600), NOW);

        assertThat(letter.getStatus()).isEqualTo(OrderDeadLetter.Status.OPEN);
        assertThat(letter.getReprocessCount()).isEqualTo(1);
        assertThat(letter.getLastReprocessedAt()).isEqualTo(NOW);
        assertThat(letter.getLastReprocessResult()).hasSize(500);
        assertThat(letter.getHandledBy()).isNull();
    }

    @Test
    @DisplayName("확인 후 종료: CLOSED · 처리자 · 처리 시각 · 메모")
    void closed() {
        OrderDeadLetter letter = OrderDeadLetter.received(received());

        letter.close(ADMIN_ID, "보낸 쪽 확인함", NOW);

        assertThat(letter.getStatus()).isEqualTo(OrderDeadLetter.Status.CLOSED);
        assertThat(letter.getHandledBy()).isEqualTo(ADMIN_ID);
        assertThat(letter.getHandledAt()).isEqualTo(NOW);
        assertThat(letter.getMemo()).isEqualTo("보낸 쪽 확인함");
    }

    @Test
    @DisplayName("OPEN이 아니면 재처리 성공 · 실패 기록 · 종료 모두 못 한다(서비스가 먼저 거른다)")
    void onlyOpenCanBeHandled() {
        OrderDeadLetter reprocessed = OrderDeadLetter.received(received());
        reprocessed.markReprocessed(ADMIN_ID, NOW);
        OrderDeadLetter closed = OrderDeadLetter.received(received());
        closed.close(ADMIN_ID, "메모", NOW);

        for (OrderDeadLetter letter : new OrderDeadLetter[] {reprocessed, closed}) {
            assertThatThrownBy(() -> letter.markReprocessed(ADMIN_ID, NOW)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> letter.recordReprocessFailure("e", NOW))
                .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> letter.close(ADMIN_ID, "메모", NOW)).isInstanceOf(IllegalStateException.class);
        }
    }

    private static ReceivedDeadLetter received() {
        return new ReceivedDeadLetter("kidly.payment.events", 1, 7L, "42", "payment.completed", "e-1", "{}",
            "payment", null, "500", "42", 42L, OrderDeadLetter.FailureType.STORE_FAILED,
            "org.springframework.dao.DataIntegrityViolationException", "실패", "trace", RECEIVED_AT);
    }
}
