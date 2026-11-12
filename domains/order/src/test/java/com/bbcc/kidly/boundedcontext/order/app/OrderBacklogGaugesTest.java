package com.bbcc.kidly.boundedcontext.order.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.bbcc.kidly.boundedcontext.order.CapturedLogs;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventFailureRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.PaymentRecoveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.stuck.StuckOrderKind;
import com.bbcc.kidly.boundedcontext.order.app.stuck.StuckOrderService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.QueryTimeoutException;

/**
 * 멈춘 주문 게이지의 요약 조회 실패 캐시(#1222): 실패해도 30초 창마다 한 번만 조회하고, 마지막 성공 값을 내며, 성공한 적이 없으면 NaN.
 * 30초가 지난 것은 expireStuckCache()로 대신한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class OrderBacklogGaugesTest {

    private static final String SECRET = "select * from orders.orders where member_id = 7600";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final StuckOrderService stuckOrderService = mock(StuckOrderService.class);
    private OrderBacklogGauges gauges;

    @BeforeEach
    void setUp() {
        gauges = new OrderBacklogGauges(registry, mock(OrderDeadLetterRepository.class),
            mock(OrderEventFailureRepository.class), mock(PaymentRecoveryRepository.class),
            mock(OrderOutboxRepository.class), stuckOrderService);
    }

    @Test
    @DisplayName("성공한 적 없이 실패하면 7개 모두 NaN(0 아님)이고, 수집 1회(7개 읽기)에 조회는 1번")
    void firstFailureIsNaN() {
        when(stuckOrderService.getSummary(any())).thenThrow(new QueryTimeoutException(SECRET));

        assertThat(collect().values()).hasSize(7).allMatch(value -> Double.isNaN(value));
        verify(stuckOrderService, times(1)).getSummary(any());
    }

    @Test
    @DisplayName("연속 실패해도 30초 안 재수집은 다시 조회하지 않고, 창이 지나면 수집 1회당 1번만 조회한다")
    void consecutiveFailuresQueryOncePerWindow() {
        when(stuckOrderService.getSummary(any())).thenThrow(new QueryTimeoutException(SECRET));

        collect();
        collect();
        verify(stuckOrderService, times(1)).getSummary(any());

        gauges.expireStuckCache();
        collect();
        collect();
        verify(stuckOrderService, times(2)).getSummary(any());
    }

    @Test
    @DisplayName("이전 성공 뒤 실패하면 이전 값을 그대로 내고, 다시 성공하면 새 값을 낸다")
    void keepsPreviousOnFailureAndTakesNewOnSuccess() {
        when(stuckOrderService.getSummary(any()))
            .thenReturn(Map.of(StuckOrderKind.STOCK_CHECK_STALE, 3L))
            .thenThrow(new QueryTimeoutException(SECRET))
            .thenReturn(Map.of(StuckOrderKind.STOCK_CHECK_STALE, 5L));

        assertThat(stuck(StuckOrderKind.STOCK_CHECK_STALE)).isEqualTo(3.0);
        assertThat(stuck(StuckOrderKind.PAYMENT_EXPIRY_MISSED)).as("요약에 없는 종류는 0").isZero();

        gauges.expireStuckCache();
        assertThat(stuck(StuckOrderKind.STOCK_CHECK_STALE)).as("실패 → 이전 값").isEqualTo(3.0);
        assertThat(stuck(StuckOrderKind.PAYMENT_EXPIRY_MISSED)).isZero();

        gauges.expireStuckCache();
        assertThat(stuck(StuckOrderKind.STOCK_CHECK_STALE)).as("다시 성공 → 새 값").isEqualTo(5.0);
        verify(stuckOrderService, times(3)).getSummary(any());
    }

    @Test
    @DisplayName("실패가 이어지면 첫 실패만 WARN 한 줄(예외 클래스 이름만, 메시지 없음)이고 나머지는 DEBUG, 다시 성공하면 복구 INFO 한 줄")
    void failureLogsOneWarnThenDebugAndRecoveryInfo(CapturedOutput output) {
        when(stuckOrderService.getSummary(any()))
            .thenThrow(new QueryTimeoutException(SECRET))
            .thenThrow(new QueryTimeoutException(SECRET))
            .thenThrow(new QueryTimeoutException(SECRET))
            .thenReturn(Map.of(StuckOrderKind.STOCK_CHECK_STALE, 1L));

        List<String> logs = withDebugLogs(() -> {
            collect();
            gauges.expireStuckCache();
            collect();
            gauges.expireStuckCache();
            collect();
            gauges.expireStuckCache();
            collect();
            collect();
        });

        verify(stuckOrderService, times(4)).getSummary(any());
        assertThat(logs).containsExactly(
            "WARN 멈춘 주문 요약 게이지 조회 실패 — 마지막 성공 값 유지: exception=QueryTimeoutException hasPrevious=false",
            "DEBUG 멈춘 주문 요약 게이지 조회 실패 계속 — 마지막 성공 값 유지: exception=QueryTimeoutException hasPrevious=false",
            "DEBUG 멈춘 주문 요약 게이지 조회 실패 계속 — 마지막 성공 값 유지: exception=QueryTimeoutException hasPrevious=false",
            "INFO 멈춘 주문 요약 게이지 조회 복구");
        assertThat(output.getAll()).doesNotContain(SECRET);
    }

    @Test
    @DisplayName("복구 뒤 다시 실패하면 그 첫 실패는 다시 WARN 한 줄(이전 값 있음)")
    void failureAfterRecoveryWarnsAgain() {
        when(stuckOrderService.getSummary(any()))
            .thenThrow(new QueryTimeoutException(SECRET))
            .thenReturn(Map.of(StuckOrderKind.STOCK_CHECK_STALE, 1L))
            .thenThrow(new QueryTimeoutException(SECRET));

        List<String> logs = withDebugLogs(() -> {
            collect();
            gauges.expireStuckCache();
            collect();
            gauges.expireStuckCache();
            collect();
        });

        assertThat(logs).containsExactly(
            "WARN 멈춘 주문 요약 게이지 조회 실패 — 마지막 성공 값 유지: exception=QueryTimeoutException hasPrevious=false",
            "INFO 멈춘 주문 요약 게이지 조회 복구",
            "WARN 멈춘 주문 요약 게이지 조회 실패 — 마지막 성공 값 유지: exception=QueryTimeoutException hasPrevious=true");
    }

    // 이 클래스 로거만 잠시 DEBUG로 열어(테스트 로그 설정은 INFO) 레벨까지 정확히 본다. 끝나면 원래 레벨로 되돌린다
    private static List<String> withDebugLogs(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(OrderBacklogGauges.class);
        Level before = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        try (CapturedLogs logs = CapturedLogs.start()) {
            action.run();
            return logs.events().stream()
                .filter(event -> event.getLoggerName().equals(OrderBacklogGauges.class.getName()))
                .map(event -> event.getLevel() + " " + event.getFormattedMessage())
                .toList();
        } finally {
            logger.setLevel(before);
        }
    }

    // 한 번의 수집: 멈춘 주문 게이지 7개를 모두 읽는다
    private Map<String, Double> collect() {
        Collection<Gauge> stuck = registry.find("kidly.order.stuck.orders").gauges();
        assertThat(stuck).hasSize(StuckOrderKind.values().length);
        return stuck.stream().collect(Collectors.toMap(gauge -> gauge.getId().getTag("kind"),
            Gauge::value));
    }

    private double stuck(StuckOrderKind kind) {
        return registry.get("kidly.order.stuck.orders").tag("kind", OrderOperationMetrics.tagValue(kind)).gauge()
            .value();
    }
}
