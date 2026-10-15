package com.bbcc.kidly.boundedcontext.order.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderSchedulerProperties;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.AutoConfirmTarget;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 한 실행 안의 ID 커서 · 종료 조건 · 실패 건 건너뜀. 저장소 대신 "ID 1..total이 대상"인 가짜 조회로 본다.
 */
@ExtendWith(MockitoExtension.class)
class OrderSchedulerTest {

    @Mock
    private OrderService orderService;

    private OrderScheduler scheduler(int batchSize, int maxPerRun) {
        return new OrderScheduler(orderService, new OrderSchedulerProperties(batchSize, maxPerRun));
    }

    /**
     * 결제 마감이 지난 주문이 ID 1..total이라고 보고, afterId 다음부터 limit건을 돌려준다.
     */
    private void givenOverdueOrders(long total) {
        when(orderService.findOverduePaymentOrderIds(any(), anyLong(), anyInt())).thenAnswer(invocation -> {
            long afterId = invocation.getArgument(1);
            int limit = invocation.getArgument(2);
            return LongStream.rangeClosed(afterId + 1, total).limit(limit).boxed().toList();
        });
    }

    @Nested
    @DisplayName("결제 마감 만료")
    class ExpireOverduePayments {

        @Test
        @DisplayName("마지막 처리 ID 다음부터 이어서 조회하고, 한 실행의 모든 조회에 같은 기준 시각을 넘긴다")
        void continuesFromLastIdWithSameNow() {
            givenOverdueOrders(5);

            scheduler(2, 10).expireOverduePayments();

            ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<Long> afterId = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
            verify(orderService, times(3))
                .findOverduePaymentOrderIds(now.capture(), afterId.capture(), limit.capture());
            assertThat(afterId.getAllValues()).containsExactly(0L, 2L, 4L);
            assertThat(limit.getAllValues()).containsExactly(2, 2, 2);
            assertThat(now.getAllValues()).containsOnly(now.getAllValues().getFirst());
            for (long id = 1; id <= 5; id++) {
                verify(orderService).cancelForPaymentExpiry(id);
            }
        }

        @ParameterizedTest(name = "대상 {0}건(batch 100) → 조회 {1}번, 처리 {0}건")
        @CsvSource({"99, 1", "100, 2", "101, 2"})
        @DisplayName("batch-size 경계: 가져온 건수가 batch-size보다 적으면 그 조회에서 끝난다")
        void stopsWhenBatchIsNotFull(long total, int fetches) {
            givenOverdueOrders(total);

            scheduler(100, 1000).expireOverduePayments();

            verify(orderService, times(fetches)).findOverduePaymentOrderIds(any(), anyLong(), anyInt());
            verify(orderService, times((int) total)).cancelForPaymentExpiry(anyLong());
        }

        @Test
        @DisplayName("max-per-run에 정확히 닿으면(batch 3 · max 6) 두 번 조회로 6건만 처리하고 멈춘다")
        void stopsExactlyAtMaxPerRun() {
            givenOverdueOrders(10);

            scheduler(3, 6).expireOverduePayments();

            ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
            verify(orderService, times(2)).findOverduePaymentOrderIds(any(), anyLong(), limit.capture());
            assertThat(limit.getAllValues()).containsExactly(3, 3);
            verify(orderService, times(6)).cancelForPaymentExpiry(anyLong());
            verify(orderService, never()).cancelForPaymentExpiry(7L);
        }

        @Test
        @DisplayName("max-per-run이 batch-size로 나누어떨어지지 않으면(batch 3 · max 7) 마지막 조회를 남은 건수(1)로 줄인다")
        void shrinksLastFetchToMaxPerRun() {
            givenOverdueOrders(10);

            scheduler(3, 7).expireOverduePayments();

            ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
            verify(orderService, times(3)).findOverduePaymentOrderIds(any(), anyLong(), limit.capture());
            assertThat(limit.getAllValues()).containsExactly(3, 3, 1);
            verify(orderService, times(7)).cancelForPaymentExpiry(anyLong());
            verify(orderService, never()).cancelForPaymentExpiry(8L);
        }

        @Test
        @DisplayName("앞쪽 건이 실패해도 건너뛰고(커서는 지나감) 뒤쪽 건을 같은 실행에서 처리한다")
        void skipsFailedAndContinues() {
            givenOverdueOrders(3);
            doThrow(new IllegalStateException("동시 변경")).when(orderService).cancelForPaymentExpiry(1L);

            scheduler(2, 10).expireOverduePayments();

            ArgumentCaptor<Long> afterId = ArgumentCaptor.forClass(Long.class);
            verify(orderService, times(2)).findOverduePaymentOrderIds(any(), afterId.capture(), anyInt());
            assertThat(afterId.getAllValues()).containsExactly(0L, 2L);
            verify(orderService).cancelForPaymentExpiry(2L);
            verify(orderService).cancelForPaymentExpiry(3L);
        }

        @Test
        @DisplayName("알려진 한계: 계속 실패하는 건이 max-per-run 이상 앞쪽에 쌓이면 매 실행 처음(afterId 0)부터 다시 시작해 뒤쪽은 처리되지 않는다")
        void persistentFailuresAtFrontBlockTheRestAcrossRuns() {
            givenOverdueOrders(6);
            for (long id = 1; id <= 4; id++) {
                doThrow(new IllegalStateException("계속 실패")).when(orderService).cancelForPaymentExpiry(id);
            }
            OrderScheduler scheduler = scheduler(2, 4);

            scheduler.expireOverduePayments();
            scheduler.expireOverduePayments();

            ArgumentCaptor<Long> afterId = ArgumentCaptor.forClass(Long.class);
            verify(orderService, times(4)).findOverduePaymentOrderIds(any(), afterId.capture(), anyInt());
            assertThat(afterId.getAllValues()).containsExactly(0L, 2L, 0L, 2L);
            verify(orderService, never()).cancelForPaymentExpiry(5L);
            verify(orderService, never()).cancelForPaymentExpiry(6L);
        }

        @Test
        @DisplayName("대상이 없으면 한 번 조회하고 아무것도 취소하지 않는다")
        void nothingToExpire() {
            givenOverdueOrders(0);

            scheduler(100, 1000).expireOverduePayments();

            verify(orderService, times(1)).findOverduePaymentOrderIds(any(), anyLong(), anyInt());
            verify(orderService, never()).cancelForPaymentExpiry(anyLong());
        }
    }

    @Nested
    @DisplayName("자동 구매확정")
    class AutoConfirm {

        /**
         * 주문상품 ID 10, 20, …, total×10(주문 ID = 주문상품 ID / 10)이 대상이라고 보고, afterId 다음부터 limit건.
         */
        private void givenTargets(long total) {
            when(orderService.findAutoConfirmTargets(any(), anyLong(), anyInt())).thenAnswer(invocation -> {
                long afterId = invocation.getArgument(1);
                int limit = invocation.getArgument(2);
                List<AutoConfirmTarget> targets = new ArrayList<>();
                for (long itemId = 10; itemId <= total * 10 && targets.size() < limit; itemId += 10) {
                    if (itemId > afterId) {
                        targets.add(new AutoConfirmTarget(itemId / 10, itemId));
                    }
                }
                return targets;
            });
        }

        @Test
        @DisplayName("커서는 주문상품 ID: 마지막 처리 주문상품 ID 다음부터 이어서 조회하고 같은 기준 시각을 넘긴다")
        void continuesFromLastOrderItemId() {
            givenTargets(3);

            scheduler(2, 10).autoConfirm();

            ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<Long> afterId = ArgumentCaptor.forClass(Long.class);
            verify(orderService, times(2)).findAutoConfirmTargets(now.capture(), afterId.capture(), anyInt());
            assertThat(afterId.getAllValues()).containsExactly(0L, 20L);
            assertThat(now.getAllValues()).containsOnly(now.getAllValues().getFirst());
            verify(orderService).confirmAutomatically(1L, 10L);
            verify(orderService).confirmAutomatically(2L, 20L);
            verify(orderService).confirmAutomatically(3L, 30L);
        }

        @Test
        @DisplayName("앞쪽 건이 실패해도 건너뛰고 뒤쪽 건을 같은 실행에서 처리하며, max-per-run에서 멈춘다")
        void skipsFailedAndStopsAtMaxPerRun() {
            givenTargets(10);
            doThrow(new IllegalStateException("동시 변경")).when(orderService).confirmAutomatically(1L, 10L);

            scheduler(2, 4).autoConfirm();

            verify(orderService, times(2)).findAutoConfirmTargets(any(), anyLong(), anyInt());
            verify(orderService).confirmAutomatically(2L, 20L);
            verify(orderService).confirmAutomatically(4L, 40L);
            verify(orderService, never()).confirmAutomatically(5L, 50L);
        }

        @Test
        @DisplayName("대상이 없으면 한 번 조회하고 아무것도 확정하지 않는다")
        void nothingToConfirm() {
            givenTargets(0);

            scheduler(100, 1000).autoConfirm();

            verify(orderService, times(1)).findAutoConfirmTargets(any(), anyLong(), anyInt());
            verify(orderService, never()).confirmAutomatically(anyLong(), anyLong());
        }
    }
}
