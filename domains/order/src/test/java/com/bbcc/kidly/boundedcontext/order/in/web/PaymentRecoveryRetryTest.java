package com.bbcc.kidly.boundedcontext.order.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.PaymentCompletionService;
import com.bbcc.kidly.boundedcontext.order.app.order.PaymentRecoveryService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PaymentRecoveryResult;
import com.bbcc.kidly.global.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class PaymentRecoveryRetryTest {

    private static final Long RECOVERY_ID = 3L;
    private static final Long ADMIN_ID = 9_000L;

    @Mock
    private PaymentRecoveryService recoveryService;

    @Mock
    private PaymentCompletionService paymentCompletion;

    private PaymentRecoveryRetry retry;

    @BeforeEach
    void setUp() {
        retry = new PaymentRecoveryRetry(recoveryService, paymentCompletion);
    }

    @Test
    @DisplayName("재처리가 끝나면 그 결과를 그대로 돌려준다")
    void returnsRecovered() {
        PaymentRecoveryResult recovered = mock(PaymentRecoveryResult.class);
        when(recoveryService.recover(eq(RECOVERY_ID), eq(ADMIN_ID), any())).thenReturn(recovered);

        assertThat(retry.retry(RECOVERY_ID, ADMIN_ID)).isSameAs(recovered);
        verify(recoveryService, never()).recordRetryFailure(any(), any(), any());
    }

    @Test
    @DisplayName("재처리가 실패하면(전부 되돌려짐) 새 트랜잭션에서 시도 횟수를 센 기록을 돌려준다")
    void countsFailure() {
        IllegalStateException error = new IllegalStateException("배송 생성 실패");
        PaymentRecoveryResult counted = mock(PaymentRecoveryResult.class);
        when(recoveryService.recover(eq(RECOVERY_ID), eq(ADMIN_ID), any())).thenThrow(error);
        when(recoveryService.recordRetryFailure(RECOVERY_ID, ADMIN_ID, error)).thenReturn(counted);

        assertThat(retry.retry(RECOVERY_ID, ADMIN_ID)).isSameAs(counted);
    }

    @Test
    @DisplayName("업무 오류(없음 · 종료됨 · 재처리 불가)는 시도 횟수를 세지 않고 그대로 던진다")
    void rethrowsBusiness() {
        BusinessException error = new BusinessException(OrderErrorCode.RECOVERY_NOT_RETRYABLE);
        when(recoveryService.recover(eq(RECOVERY_ID), eq(ADMIN_ID), any())).thenThrow(error);

        assertThatThrownBy(() -> retry.retry(RECOVERY_ID, ADMIN_ID)).isSameAs(error);
        verify(recoveryService, never()).recordRetryFailure(any(), any(), any());
    }

    @Test
    @DisplayName("동시 처리에서 진 쪽(낙관적 락 · 배송 유니크)은 시도 횟수를 세지 않고 그대로 던진다(409)")
    void rethrowsLostConcurrentDuplicate() {
        ObjectOptimisticLockingFailureException lock = new ObjectOptimisticLockingFailureException("Order", 1L);
        DataIntegrityViolationException unique = new DataIntegrityViolationException(
            "duplicate key value violates unique constraint \"uk_delivery_order_seller\"");
        when(recoveryService.recover(eq(RECOVERY_ID), eq(ADMIN_ID), any())).thenThrow(lock).thenThrow(unique);

        assertThatThrownBy(() -> retry.retry(RECOVERY_ID, ADMIN_ID)).isSameAs(lock);
        assertThatThrownBy(() -> retry.retry(RECOVERY_ID, ADMIN_ID)).isSameAs(unique);
        verify(recoveryService, never()).recordRetryFailure(any(), any(), any());
    }
}
