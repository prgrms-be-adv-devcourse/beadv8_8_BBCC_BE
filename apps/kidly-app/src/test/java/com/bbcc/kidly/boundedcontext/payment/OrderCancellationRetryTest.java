package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.ORDER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.domain.OrderCancellationRetry;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderCancellationRetryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 10, 0);

    @Test
    @DisplayName("처음 실패한 취소는 시도 1회와 오류 · 실패 시각을 남긴다")
    void failed() {
        OrderCancellationRetry retry =
            OrderCancellationRetry.failed(ORDER_ID, MEMBER_ID, "PAYMENT_EXPIRED", "DB 오류", NOW);

        assertThat(retry.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(retry.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(retry.getReason()).isEqualTo("PAYMENT_EXPIRED");
        assertThat(retry.getAttemptCount()).isEqualTo(1);
        assertThat(retry.getLastError()).isEqualTo("DB 오류");
        assertThat(retry.getLastFailedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("다시 실패하면 시도 횟수를 늘리고 마지막 오류 · 실패 시각을 바꾼다")
    void failAgain() {
        OrderCancellationRetry retry =
            OrderCancellationRetry.failed(ORDER_ID, MEMBER_ID, "PAYMENT_EXPIRED", "DB 오류", NOW);

        retry.fail("락 대기 시간 초과", NOW.plusMinutes(1));

        assertThat(retry.getAttemptCount()).isEqualTo(2);
        assertThat(retry.getLastError()).isEqualTo("락 대기 시간 초과");
        assertThat(retry.getLastFailedAt()).isEqualTo(NOW.plusMinutes(1));
    }

    @Test
    @DisplayName("오류 메시지는 500자까지만 남긴다")
    void truncateError() {
        OrderCancellationRetry retry = OrderCancellationRetry.failed(ORDER_ID, MEMBER_ID, null, "가".repeat(600), NOW);

        assertThat(retry.getLastError()).hasSize(500);
    }
}
