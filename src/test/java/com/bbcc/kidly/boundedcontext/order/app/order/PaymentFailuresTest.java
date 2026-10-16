package com.bbcc.kidly.boundedcontext.order.app.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.domain.order.PaymentMismatchException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class PaymentFailuresTest {

    @Test
    @DisplayName("낙관적 락 · 배송 (order_id, seller_id) 유니크 위반은 동시 중복 처리에서 진 쪽")
    void lostConcurrentDuplicate() {
        assertThat(PaymentFailures.isLostConcurrentDuplicate(
            new ObjectOptimisticLockingFailureException("Order", 1L))).isTrue();
        assertThat(PaymentFailures.isLostConcurrentDuplicate(new DataIntegrityViolationException("x",
            new RuntimeException("duplicate key value violates unique constraint \"uk_delivery_order_seller\""))))
            .isTrue();
    }

    @Test
    @DisplayName("다른 제약 위반 · 그 밖 예외 · 결제 불일치는 진 쪽이 아니다")
    void notLostConcurrentDuplicate() {
        assertThat(PaymentFailures.isLostConcurrentDuplicate(new DataIntegrityViolationException("x",
            new RuntimeException("null value in column \"seller_id\" violates not-null constraint")))).isFalse();
        assertThat(PaymentFailures.isLostConcurrentDuplicate(new IllegalStateException("x"))).isFalse();
        assertThat(PaymentFailures.isLostConcurrentDuplicate(mismatch())).isFalse();
    }

    @Test
    @DisplayName("실패 이유 코드: 결제 불일치는 사유 코드, 그 밖은 예외 클래스 이름(공백 없음)")
    void failureCode() {
        assertThat(PaymentFailures.failureCode(mismatch())).isEqualTo("member-mismatch");
        assertThat(PaymentFailures.failureCode(new DataIntegrityViolationException("x")))
            .isEqualTo("DataIntegrityViolationException");
        assertThat(PaymentFailures.failureCode(new IllegalStateException("공백 있는 메시지")))
            .isEqualTo("IllegalStateException");
    }

    @Test
    @DisplayName("기록 저장이 결제 반영 실패 기록 유니크(uk_payment_recovery_order)에 걸리면 이미 보관됨, 그 밖은 아님")
    void alreadyRecorded() {
        assertThat(PaymentFailures.isAlreadyRecorded(new DataIntegrityViolationException("x",
            new RuntimeException("duplicate key value violates unique constraint \"uk_payment_recovery_order\""))))
            .isTrue();
        assertThat(PaymentFailures.isAlreadyRecorded(new DataIntegrityViolationException(
            "duplicate key value violates unique constraint \"uk_delivery_order_seller\""))).isFalse();
        assertThat(PaymentFailures.isAlreadyRecorded(new IllegalStateException("uk_payment_recovery_order"))).isFalse();
    }

    @Test
    @DisplayName("결제 불일치만 재처리할 수 없다")
    void retryable() {
        assertThat(PaymentFailures.isRetryable(mismatch())).isFalse();
        assertThat(PaymentFailures.isRetryable(new DataIntegrityViolationException("x"))).isTrue();
        assertThat(PaymentFailures.isRetryable(new IllegalStateException("x"))).isTrue();
    }

    private static PaymentMismatchException mismatch() {
        return new PaymentMismatchException(1L, PaymentMismatchException.Reason.MEMBER_MISMATCH, "expected=1 actual=2");
    }
}
