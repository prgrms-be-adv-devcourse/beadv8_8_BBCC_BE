package com.bbcc.kidly.boundedcontext.order.domain.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PaymentRecoveryTest {

    private static final LocalDateTime NOW = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    private static final List<PaymentRecovery.PaidItem> ITEMS = List.of(
        new PaymentRecovery.PaidItem(41L, 25_000L), new PaymentRecovery.PaidItem(42L, 30_000L));

    @Nested
    @DisplayName("처음 보관")
    class Open {

        @Test
        @DisplayName("재처리할 수 있는 실패면 PENDING · 시도 1 · 받은 결제 완료 내용 · 실패 이유 · 시도 시각을 그대로 담는다")
        void retryableIsPending() {
            PaymentRecovery recovery = open(true);

            assertThat(recovery.getStatus()).isEqualTo(PaymentRecovery.Status.PENDING);
            assertThat(recovery.getOrderId()).isEqualTo(12L);
            assertThat(recovery.getPaymentId()).isEqualTo(500L);
            assertThat(recovery.getMemberId()).isEqualTo(1L);
            assertThat(recovery.getAmount()).isEqualTo(55_000L);
            assertThat(recovery.getItems()).containsExactlyElementsOf(ITEMS);
            assertThat(recovery.getFailureCode()).isEqualTo("DataIntegrityViolationException");
            assertThat(recovery.getAttemptCount()).isEqualTo(1);
            assertThat(recovery.getLastAttemptedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("결제 내용 불일치처럼 재처리할 수 없는 실패면 NOT_RETRYABLE")
        void notRetryable() {
            assertThat(open(false).getStatus()).isEqualTo(PaymentRecovery.Status.NOT_RETRYABLE);
        }
    }

    @Nested
    @DisplayName("다시 실패")
    class FailAgain {

        @Test
        @DisplayName("PENDING이면 시도 +1 · 실패 이유 · 시도 시각만 바뀌고 결제 내용은 처음 값 그대로")
        void pendingCountsAttempt() {
            PaymentRecovery recovery = open(true);

            recovery.recordFailure("IllegalStateException", true, NOW.plusMinutes(1));

            assertThat(recovery.getStatus()).isEqualTo(PaymentRecovery.Status.PENDING);
            assertThat(recovery.getAttemptCount()).isEqualTo(2);
            assertThat(recovery.getFailureCode()).isEqualTo("IllegalStateException");
            assertThat(recovery.getLastAttemptedAt()).isEqualTo(NOW.plusMinutes(1));
            assertThat(recovery.getPaymentId()).isEqualTo(500L);
            assertThat(recovery.getItems()).containsExactlyElementsOf(ITEMS);
        }

        @Test
        @DisplayName("PENDING에서 재처리할 수 없는 실패가 나면 NOT_RETRYABLE")
        void pendingBecomesNotRetryable() {
            PaymentRecovery recovery = open(true);

            recovery.recordFailure("member-mismatch", false, NOW.plusMinutes(1));

            assertThat(recovery.getStatus()).isEqualTo(PaymentRecovery.Status.NOT_RETRYABLE);
            assertThat(recovery.getAttemptCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("NOT_RETRYABLE은 재처리할 수 있는 실패가 와도 NOT_RETRYABLE로 남는다(결제 내용이 이미 맞지 않음)")
        void notRetryableStays() {
            PaymentRecovery recovery = open(false);

            recovery.recordFailure("IllegalStateException", true, NOW.plusMinutes(1));

            assertThat(recovery.getStatus()).isEqualTo(PaymentRecovery.Status.NOT_RETRYABLE);
            assertThat(recovery.getAttemptCount()).isEqualTo(2);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = PaymentRecovery.Status.class, names = {"RECOVERED", "CANCELLED"})
        @DisplayName("이미 종료된 기록(RECOVERED · CANCELLED)은 실패가 와도 바뀌지 않는다")
        void closedIgnoresFailure(PaymentRecovery.Status closed) {
            PaymentRecovery recovery = closed(closed);

            recovery.recordFailure("IllegalStateException", true, NOW.plusMinutes(5));

            assertThat(recovery.getStatus()).isEqualTo(closed);
            assertThat(recovery.getAttemptCount()).isEqualTo(1);
            assertThat(recovery.getFailureCode()).isEqualTo("DataIntegrityViolationException");
        }
    }

    @Nested
    @DisplayName("복구 · 취소로 종료")
    class Close {

        @Test
        @DisplayName("PENDING을 복구하면 RECOVERED, 시도 시각이 바뀐다(시도 횟수는 실패만 센다)")
        void recover() {
            PaymentRecovery recovery = open(true);

            recovery.recover(NOW.plusMinutes(1));

            assertThat(recovery.getStatus()).isEqualTo(PaymentRecovery.Status.RECOVERED);
            assertThat(recovery.getLastAttemptedAt()).isEqualTo(NOW.plusMinutes(1));
            assertThat(recovery.getAttemptCount()).isEqualTo(1);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = PaymentRecovery.Status.class, names = {"PENDING", "NOT_RETRYABLE"})
        @DisplayName("PENDING · NOT_RETRYABLE은 취소로 종료할 수 있다(CANCELLED)")
        void cancel(PaymentRecovery.Status from) {
            PaymentRecovery recovery = open(from == PaymentRecovery.Status.PENDING);

            recovery.cancel(NOW.plusMinutes(1));

            assertThat(recovery.getStatus()).isEqualTo(PaymentRecovery.Status.CANCELLED);
            assertThat(recovery.getLastAttemptedAt()).isEqualTo(NOW.plusMinutes(1));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = PaymentRecovery.Status.class, names = {"NOT_RETRYABLE", "RECOVERED", "CANCELLED"})
        @DisplayName("PENDING이 아니면 복구할 수 없다(서비스가 먼저 거르므로 여기 오면 불변식 위반)")
        void recoverOnlyPending(PaymentRecovery.Status from) {
            PaymentRecovery recovery = from == PaymentRecovery.Status.NOT_RETRYABLE ? open(false) : closed(from);

            assertThatThrownBy(() -> recovery.recover(NOW.plusMinutes(1))).isInstanceOf(IllegalStateException.class);
            assertThat(recovery.getStatus()).isEqualTo(from);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = PaymentRecovery.Status.class, names = {"RECOVERED", "CANCELLED"})
        @DisplayName("종료된 기록은 취소로 종료할 수 없다(서비스가 먼저 거르므로 여기 오면 불변식 위반)")
        void cancelOnlyOpen(PaymentRecovery.Status from) {
            PaymentRecovery recovery = closed(from);

            assertThatThrownBy(() -> recovery.cancel(NOW.plusMinutes(1))).isInstanceOf(IllegalStateException.class);
            assertThat(recovery.getStatus()).isEqualTo(from);
        }
    }

    private static PaymentRecovery open(boolean retryable) {
        return PaymentRecovery.open(12L, 500L, 1L, 55_000L, ITEMS, "DataIntegrityViolationException", retryable, NOW);
    }

    private static PaymentRecovery closed(PaymentRecovery.Status status) {
        PaymentRecovery recovery = open(true);
        if (status == PaymentRecovery.Status.RECOVERED) {
            recovery.recover(NOW);
        } else {
            recovery.cancel(NOW);
        }
        return recovery;
    }
}
