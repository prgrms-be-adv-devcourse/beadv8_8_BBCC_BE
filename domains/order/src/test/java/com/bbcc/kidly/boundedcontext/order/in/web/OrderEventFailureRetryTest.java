package com.bbcc.kidly.boundedcontext.order.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderEventFailureService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderEventFailureResult;
import com.bbcc.kidly.global.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class OrderEventFailureRetryTest {

    private static final Long FAILURE_ID = 3L;
    private static final Long ADMIN_ID = 9_000L;

    @Mock
    private OrderEventFailureService failureService;

    private OrderEventFailureRetry retry;

    @BeforeEach
    void setUp() {
        retry = new OrderEventFailureRetry(failureService);
    }

    @Test
    @DisplayName("재처리가 끝나면 그 결과를 그대로 돌려준다")
    void returnsRecovered() {
        OrderEventFailureResult recovered = mock(OrderEventFailureResult.class);
        when(failureService.recover(FAILURE_ID, ADMIN_ID)).thenReturn(recovered);

        assertThat(retry.retry(FAILURE_ID, ADMIN_ID)).isSameAs(recovered);
        verify(failureService, never()).recordRetryFailure(any(), any());
    }

    @Test
    @DisplayName("재처리가 실패하면(전부 되돌려짐) 새 트랜잭션에서 시도 횟수를 센 기록을 돌려준다")
    void countsFailure() {
        IllegalStateException error = new IllegalStateException("주문 갱신 실패");
        OrderEventFailureResult counted = mock(OrderEventFailureResult.class);
        when(failureService.recover(FAILURE_ID, ADMIN_ID)).thenThrow(error);
        when(failureService.recordRetryFailure(FAILURE_ID, error)).thenReturn(counted);

        assertThat(retry.retry(FAILURE_ID, ADMIN_ID)).isSameAs(counted);
    }

    @Test
    @DisplayName("업무 오류(없음 · 종료됨)와 동시 처리에서 진 쪽(낙관적 락)은 시도 횟수를 세지 않고 그대로 던진다")
    void rethrowsBusinessAndLost() {
        BusinessException closed = new BusinessException(OrderErrorCode.EVENT_FAILURE_CLOSED);
        ObjectOptimisticLockingFailureException lock = new ObjectOptimisticLockingFailureException("Order", 1L);
        when(failureService.recover(FAILURE_ID, ADMIN_ID)).thenThrow(closed).thenThrow(lock);

        assertThatThrownBy(() -> retry.retry(FAILURE_ID, ADMIN_ID)).isSameAs(closed);
        assertThatThrownBy(() -> retry.retry(FAILURE_ID, ADMIN_ID)).isSameAs(lock);
        verify(failureService, never()).recordRetryFailure(any(), any());
    }
}
