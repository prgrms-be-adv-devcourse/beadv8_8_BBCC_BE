package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.domain.PaymentOutboxMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentOutboxMessageTest {

    @Test
    @DisplayName("만들면 아직 보내지 않았고 시도는 0이다")
    void create() {
        PaymentOutboxMessage message = PaymentFixture.outboxMessage();

        assertThat(message.getSentAt()).isNull();
        assertThat(message.getAttemptCount()).isZero();
        assertThat(message.getLastError()).isNull();
    }

    @Test
    @DisplayName("보내면 시도를 1 늘리고 보낸 시각을 남긴다")
    void markSent() {
        PaymentOutboxMessage message = PaymentFixture.outboxMessage();

        message.markSent(NOW);

        assertThat(message.getSentAt()).isEqualTo(NOW);
        assertThat(message.getAttemptCount()).isOne();
    }

    @Test
    @DisplayName("이미 보낸 메시지는 다시 보낸 것으로 기록해도 처음 기록이 남는다")
    void markSentOnce() {
        PaymentOutboxMessage message = PaymentFixture.outboxMessage();
        message.markSent(NOW);

        message.markSent(NOW.plusMinutes(1));

        assertThat(message.getSentAt()).isEqualTo(NOW);
        assertThat(message.getAttemptCount()).isOne();
    }

    @Test
    @DisplayName("보내지 못하면 시도를 1 늘리고 마지막 오류를 바꾼다. 보낸 시각은 비어 있다")
    void recordFailure() {
        PaymentOutboxMessage message = PaymentFixture.outboxMessage();
        message.recordFailure("첫 오류");

        message.recordFailure("둘째 오류");

        assertThat(message.getAttemptCount()).isEqualTo(2);
        assertThat(message.getLastError()).isEqualTo("둘째 오류");
        assertThat(message.getSentAt()).isNull();
    }

    @Test
    @DisplayName("오류가 500자를 넘으면 앞 500자만 남긴다")
    void truncateError() {
        PaymentOutboxMessage message = PaymentFixture.outboxMessage();

        message.recordFailure("가".repeat(501));

        assertThat(message.getLastError()).hasSize(500);
    }
}
