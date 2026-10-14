package com.bbcc.kidly.boundedcontext.order.in.scheduler;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderSchedulerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderScheduler scheduler;

    @Nested
    @DisplayName("결제 마감 만료")
    class ExpireOverduePayments {

        @Test
        @DisplayName("대상 주문을 한 건씩 만료 취소하고, 한 건이 실패해도 다음 건을 계속 처리한다")
        void continuesAfterFailure() {
            when(orderService.findOverduePaymentOrderIds()).thenReturn(List.of(1L, 2L, 3L));
            doThrow(new IllegalStateException("동시 변경")).when(orderService).cancelForPaymentExpiry(2L);

            scheduler.expireOverduePayments();

            verify(orderService).cancelForPaymentExpiry(1L);
            verify(orderService).cancelForPaymentExpiry(2L);
            verify(orderService).cancelForPaymentExpiry(3L);
        }

        @Test
        @DisplayName("대상이 없으면 아무것도 취소하지 않는다")
        void nothingToExpire() {
            scheduler.expireOverduePayments();

            verify(orderService, never()).cancelForPaymentExpiry(anyLong());
        }
    }
}
