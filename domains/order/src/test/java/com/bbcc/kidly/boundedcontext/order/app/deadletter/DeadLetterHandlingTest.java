package com.bbcc.kidly.boundedcontext.order.app.deadletter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.DeadLetter;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.FailureType;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.Handling;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.Status;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DLT 메시지 관리자 처리 규칙(#601, #642: 보관 행은 out, 처리 규칙은 app). 스프링 없이 값만 본다.
 */
class DeadLetterHandlingTest {

    private static final LocalDateTime RECEIVED_AT = LocalDateTime.of(2026, 10, 31, 9, 0);
    private static final LocalDateTime NOW = RECEIVED_AT.plusHours(1);
    private static final Long ADMIN_ID = 99L;

    @Test
    @DisplayName("재처리 성공: REPROCESSED · 처리자 · 처리 시각, 재처리 횟수 +1 · 마지막 재처리 시각, 마지막 실패 이유는 지운다")
    void reprocessed() {
        Handling failed = DeadLetterHandling.reprocessFailed(open(), "InvalidFormatException",
            RECEIVED_AT.plusMinutes(5));

        Handling handling = DeadLetterHandling.reprocessed(open().withHandling(failed), ADMIN_ID, NOW);

        assertThat(handling.status()).isEqualTo(Status.REPROCESSED);
        assertThat(handling.handledBy()).isEqualTo(ADMIN_ID);
        assertThat(handling.handledAt()).isEqualTo(NOW);
        assertThat(handling.reprocessCount()).isEqualTo(2);
        assertThat(handling.lastReprocessedAt()).isEqualTo(NOW);
        assertThat(handling.lastReprocessResult()).isNull();
    }

    @Test
    @DisplayName("재처리에서 원래 처리가 주문 자체 보관으로 넘겼다: REPROCESSED · 처리자 · 시각, 결과에 넘김 표시")
    void handedOff() {
        Handling handling = DeadLetterHandling.handedOff(open(), ADMIN_ID, NOW);

        assertThat(handling.status()).isEqualTo(Status.REPROCESSED);
        assertThat(handling.handledBy()).isEqualTo(ADMIN_ID);
        assertThat(handling.reprocessCount()).isEqualTo(1);
        assertThat(handling.lastReprocessResult()).isEqualTo("자체 보관으로 넘김(재처리 대기 목록에서 처리)");
    }

    @Test
    @DisplayName("재처리 실패: OPEN 그대로, 재처리 횟수 +1 · 마지막 재처리 시각 · 실패 이유(500자까지), 처리자는 비어 있다")
    void reprocessFailed() {
        Handling handling = DeadLetterHandling.reprocessFailed(open(), "x".repeat(600), NOW);

        assertThat(handling.status()).isEqualTo(Status.OPEN);
        assertThat(handling.reprocessCount()).isEqualTo(1);
        assertThat(handling.lastReprocessedAt()).isEqualTo(NOW);
        assertThat(handling.lastReprocessResult()).hasSize(500);
        assertThat(handling.handledBy()).isNull();
    }

    @Test
    @DisplayName("확인 후 종료: CLOSED · 처리자 · 처리 시각 · 메모")
    void closed() {
        Handling handling = DeadLetterHandling.closed(open(), ADMIN_ID, "보낸 쪽 확인함", NOW);

        assertThat(handling.status()).isEqualTo(Status.CLOSED);
        assertThat(handling.handledBy()).isEqualTo(ADMIN_ID);
        assertThat(handling.handledAt()).isEqualTo(NOW);
        assertThat(handling.memo()).isEqualTo("보낸 쪽 확인함");
    }

    @Test
    @DisplayName("OPEN이 아니면 재처리 성공 · 실패 기록 · 종료 모두 못 한다(서비스가 먼저 거른다)")
    void onlyOpenCanBeHandled() {
        DeadLetter reprocessed = open().withHandling(DeadLetterHandling.reprocessed(open(), ADMIN_ID, NOW));
        DeadLetter closed = open().withHandling(DeadLetterHandling.closed(open(), ADMIN_ID, "메모", NOW));

        for (DeadLetter letter : new DeadLetter[] {reprocessed, closed}) {
            assertThatThrownBy(() -> DeadLetterHandling.reprocessed(letter, ADMIN_ID, NOW))
                .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> DeadLetterHandling.reprocessFailed(letter, "e", NOW))
                .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> DeadLetterHandling.closed(letter, ADMIN_ID, "메모", NOW))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("결제 토픽에서 온 메시지인지(#632)")
    void paymentEvent() {
        assertThat(DeadLetterHandling.isPaymentEvent(open())).isTrue();
        assertThat(DeadLetterHandling.isPaymentEvent(new DeadLetter(1L, "kidly.product.events", 0, 1L, null, null,
            null, null, null, null, null, null, null, FailureType.READ_FAILED, null, null, null, RECEIVED_AT,
            RECEIVED_AT, 1, open().handling()))).isFalse();
    }

    private static DeadLetter open() {
        return new DeadLetter(1L, "kidly.payment.events", 1, 7L, "42", "payment.completed", "e-1", "{}", "payment",
            null, "500", "42", 42L, FailureType.STORE_FAILED,
            "org.springframework.dao.DataIntegrityViolationException", "실패", "trace", RECEIVED_AT, RECEIVED_AT, 1,
            new Handling(Status.OPEN, null, null, null, 0, null, null));
    }
}
