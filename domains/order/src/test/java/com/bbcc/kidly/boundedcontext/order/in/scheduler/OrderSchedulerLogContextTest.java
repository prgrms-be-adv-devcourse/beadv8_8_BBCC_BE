package com.bbcc.kidly.boundedcontext.order.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.CapturedLogs;
import com.bbcc.kidly.boundedcontext.order.ImmediateJobRunner;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderScheduleIntervals;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderSchedulerProperties;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.PaymentExpiryGate;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.AutoConfirmTarget;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

/**
 * 주문 스케줄러 로그의 추적 값(#603): 대상 하나를 처리하는 동안 correlationId = 주문 ID(요청 밖이라 traceId도 주문 ID),
 * 다음 대상 · 실행 요약 · 실행 뒤에는 남기지 않는다. 처리 순간의 MDC는 서비스 대역이 기록한다.
 */
@ExtendWith(MockitoExtension.class)
class OrderSchedulerLogContextTest {

    private static final OrderScheduleIntervals INTERVALS = new OrderScheduleIntervals(
        Duration.ofMinutes(1), new OrderScheduleIntervals.AutoConfirm(Duration.ofMinutes(10)));

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentExpiryGate paymentExpiryGate;

    private final Map<Long, Map<String, String>> processedWith = new LinkedHashMap<>();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("결제 마감 만료: 주문마다 correlationId · traceId = 주문 ID, 한 건이 실패해도 그 실패 로그에 값이 붙고 다음 건에 안 남으며 실행 뒤 비운다")
    void expiryPerOrder() {
        when(paymentExpiryGate.allowsRun()).thenReturn(true);
        when(orderService.findOverduePaymentOrderIds(any(), anyLong(), anyInt())).thenReturn(List.of(1L, 2L, 3L));
        doAnswer(invocation -> {
            Long orderId = invocation.getArgument(0);
            processedWith.put(orderId, new HashMap<>(MDC.getCopyOfContextMap()));
            if (orderId == 2L) {
                throw new IllegalStateException("동시 변경");
            }
            return null;
        }).when(orderService).cancelForPaymentExpiry(anyLong());

        try (CapturedLogs logs = CapturedLogs.start()) {
            scheduler().expireOverduePayments();

            assertThat(processedWith).containsExactly(
                Map.entry(1L, Map.of("correlationId", "1", "traceId", "1")),
                Map.entry(2L, Map.of("correlationId", "2", "traceId", "2")),
                Map.entry(3L, Map.of("correlationId", "3", "traceId", "3")));
            assertThat(logs.mdcOf("결제 마감 만료 취소 실패: orderId=2").orElseThrow())
                .containsEntry("correlationId", "2");
            assertThat(logs.mdcOf("스케줄러 실행: job=payment-expiry").orElseThrow())
                .as("실행 요약은 대상 밖").doesNotContainKeys("correlationId", "traceId");
        }
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("자동 구매확정: correlationId는 주문상품 ID가 아니라 주문 ID")
    void autoConfirmUsesOrderId() {
        when(orderService.findAutoConfirmTargets(any(), anyLong(), anyInt())).thenReturn(List.of(
            new AutoConfirmTarget(1L, 10L), new AutoConfirmTarget(2L, 20L)));
        doAnswer(invocation -> {
            processedWith.put(invocation.getArgument(1), new HashMap<>(MDC.getCopyOfContextMap()));
            return null;
        }).when(orderService).confirmAutomatically(anyLong(), anyLong());

        scheduler().autoConfirm();

        assertThat(processedWith).containsExactly(
            Map.entry(10L, Map.of("correlationId", "1", "traceId", "1")),
            Map.entry(20L, Map.of("correlationId", "2", "traceId", "2")));
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("이미 traceId가 있으면(요청 안에서 부름) 그대로 두고, 실행 뒤에도 요청 값만 남는다")
    void keepsRequestTraceId() {
        when(paymentExpiryGate.allowsRun()).thenReturn(true);
        when(orderService.findOverduePaymentOrderIds(any(), anyLong(), anyInt())).thenReturn(List.of(1L));
        doAnswer(invocation -> {
            processedWith.put(invocation.getArgument(0), new HashMap<>(MDC.getCopyOfContextMap()));
            return null;
        }).when(orderService).cancelForPaymentExpiry(anyLong());
        MDC.put("traceId", "request-trace");

        scheduler().expireOverduePayments();

        assertThat(processedWith.get(1L)).containsEntry("traceId", "request-trace").containsEntry("correlationId", "1");
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(Map.of("traceId", "request-trace"));
    }

    private OrderScheduler scheduler() {
        return new OrderScheduler(orderService, new OrderSchedulerProperties(100, 1000), INTERVALS,
            paymentExpiryGate, new ImmediateJobRunner());
    }
}
