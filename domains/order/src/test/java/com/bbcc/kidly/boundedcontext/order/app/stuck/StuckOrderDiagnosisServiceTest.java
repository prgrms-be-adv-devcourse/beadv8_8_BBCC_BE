package com.bbcc.kidly.boundedcontext.order.app.stuck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.config.StuckOrderDiagnosisProperties;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventFailureRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderStatusHistoryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.PaymentRecoveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnoser;
import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnoser.Diagnosis;
import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnosisRepository;
import com.bbcc.kidly.boundedcontext.order.app.stuck.dto.StuckOrderResult;
import com.bbcc.kidly.boundedcontext.order.domain.history.OrderStatusHistory;
import com.bbcc.kidly.boundedcontext.order.domain.stuck.StuckOrderDiagnosis;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * 멈춘 주문 AI 진단(#926)의 요청 규칙: 새 건만 · 상태가 바뀌기 전까지 한 번 · 앞 페이지가 모두 요청한 건이어도 다음 페이지 ·
 * 하루/한 실행 상한 · 실패는 기록하고 계속 · 약속을 어긴 결과는 실패로 · 부르지 못하면 멈춤 · 꺼지면 아무것도 안 함.
 */
class StuckOrderDiagnosisServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 15, 0);
    private static final String KIND = StuckOrderKind.PAID_WITHOUT_DELIVERY.name();
    private static final int SCAN = 50;

    private final StuckOrderService stuckOrderService = mock(StuckOrderService.class);
    private final StuckOrderDiagnoser diagnoser = mock(StuckOrderDiagnoser.class);
    private final StuckOrderDiagnosisRepository repository = mock(StuckOrderDiagnosisRepository.class);
    private final OrderStatusHistoryRepository historyRepository = mock(OrderStatusHistoryRepository.class);
    private final OrderEventFailureRepository failureRepository = mock(OrderEventFailureRepository.class);
    private final PaymentRecoveryRepository recoveryRepository = mock(PaymentRecoveryRepository.class);
    private final List<StuckOrderDiagnosis> saved = new ArrayList<>();
    // 주문 → 마지막 이력 ID, 이미 요청한 "주문:이력 ID"
    private final Map<Long, Long> lastHistoryIds = new HashMap<>();
    private final Set<String> requested = new HashSet<>();

    @BeforeEach
    void setUp() {
        when(diagnoser.isAvailable()).thenReturn(true);
        when(stuckOrderService.findStuckOrders(anyString(), any(), anyInt(), anyInt())).thenReturn(Page.empty());
        when(failureRepository.findByOrderIdAndEventType(anyLong(), any())).thenReturn(Optional.empty());
        when(recoveryRepository.findByOrderId(anyLong())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(historyRepository.findLastHistoryIds(anyCollection())).thenAnswer(invocation -> {
            Collection<Long> orderIds = invocation.getArgument(0);
            return orderIds.stream().filter(lastHistoryIds::containsKey)
                .map(orderId -> lastHistory(orderId, lastHistoryIds.get(orderId))).toList();
        });
        when(repository.findRequestedStates(eq(KIND), anyCollection())).thenAnswer(invocation -> {
            Collection<Long> orderIds = invocation.getArgument(1);
            return requested.stream().map(key -> key.split(":"))
                .filter(key -> orderIds.contains(Long.valueOf(key[0])))
                .map(key -> requestedState(Long.valueOf(key[0]), Long.parseLong(key[1]))).toList();
        });
        when(diagnoser.diagnose(any())).thenReturn(Diagnosis.diagnosed("요약", "조치",
            StuckOrderDiagnoser.Confidence.HIGH, "fake", "m"));
    }

    private StuckOrderDiagnosisService service(int dailyLimit, int maxPerRun) {
        return service(dailyLimit, maxPerRun, 20);
    }

    private StuckOrderDiagnosisService service(int dailyLimit, int maxPerRun, int maxScanPages) {
        return new StuckOrderDiagnosisService(stuckOrderService, diagnoser, repository, historyRepository,
            failureRepository, recoveryRepository,
            new StuckOrderDiagnosisProperties(dailyLimit, maxPerRun, SCAN, maxScanPages));
    }

    // 한 종류의 멈춘 주문들(오래된 것부터). SCAN씩 페이지로 나눠 돌려준다
    private void stuck(List<Long> orderIds) {
        List<StuckOrderResult> rows = orderIds.stream()
            .map(orderId -> new StuckOrderResult(KIND, orderId, "ORDER", orderId, "PAID", NOW.minusHours(1)))
            .toList();
        for (int page = 0; page * SCAN < Math.max(rows.size(), 1); page++) {
            List<StuckOrderResult> content = rows.subList(page * SCAN, Math.min(rows.size(), (page + 1) * SCAN));
            when(stuckOrderService.findStuckOrders(eq(KIND), eq(NOW), eq(page), eq(SCAN)))
                .thenReturn(new PageImpl<>(content, PageRequest.of(page, SCAN), rows.size()));
        }
        orderIds.forEach(orderId -> history(orderId, orderId * 10));
    }

    private void stuck(Long... orderIds) {
        stuck(List.of(orderIds));
    }

    private void history(Long orderId, long lastHistoryId) {
        OrderStatusHistory row = mock(OrderStatusHistory.class);
        when(row.getId()).thenReturn(lastHistoryId);
        when(row.getTargetType()).thenReturn(OrderStatusHistory.TargetType.ORDER);
        when(row.getActorType()).thenReturn(OrderStatusHistory.ActorType.EVENT);
        when(row.getToStatus()).thenReturn("PAID");
        when(row.getSource()).thenReturn("payment.completed");
        when(row.getOccurredAt()).thenReturn(NOW.minusHours(2));
        when(historyRepository.findAllByOrderIdOrderByOccurredAtAscIdAsc(orderId)).thenReturn(List.of(row));
        lastHistoryIds.put(orderId, lastHistoryId);
    }

    @Test
    @DisplayName("새로 멈춘 대상을 진단해 (주문, 종류, 마지막 이력 ID)로 메모를 남긴다. 요청 값은 주문 쪽 값뿐")
    void diagnosesNew() {
        stuck(1L);

        StuckOrderDiagnosisService.Run run = service(100, 10).diagnoseNew(NOW);

        assertThat(run).isEqualTo(new StuckOrderDiagnosisService.Run(1, 1, 0, false, false));
        assertThat(saved).singleElement().satisfies(memo -> {
            assertThat(memo.getOrderId()).isEqualTo(1L);
            assertThat(memo.getStuckKind()).isEqualTo(KIND);
            assertThat(memo.getHistoryMark()).isEqualTo(10L);
            assertThat(memo.getStatus()).isEqualTo(StuckOrderDiagnosis.Status.DIAGNOSED);
            assertThat(memo.getConfidence()).isEqualTo(StuckOrderDiagnosis.Confidence.HIGH);
        });
        ArgumentCaptor<StuckOrderDiagnoser.Request> request =
            ArgumentCaptor.forClass(StuckOrderDiagnoser.Request.class);
        verify(diagnoser).diagnose(request.capture());
        assertThat(request.getValue().orderStatus()).isEqualTo("PAID");
        assertThat(request.getValue().history()).singleElement()
            .satisfies(line -> assertThat(line.source()).isEqualTo("payment.completed"));
    }

    @Test
    @DisplayName("같은 상태로 이미 요청했으면(성공 · 실패 모두) 다시 부르지 않고 이력도 다시 읽지 않는다")
    void skipsAlreadyRequested() {
        stuck(1L);
        requested.add("1:10");

        StuckOrderDiagnosisService.Run run = service(100, 10).diagnoseNew(NOW);

        assertThat(run.requested()).isZero();
        verify(diagnoser, never()).diagnose(any());
        verify(historyRepository, never()).findAllByOrderIdOrderByOccurredAtAscIdAsc(anyLong());
    }

    @Test
    @DisplayName("상태가 바뀌면(마지막 이력 ID가 다르면) 다시 요청한다")
    void requestsAgainAfterStateChange() {
        stuck(1L);
        requested.add("1:10");
        history(1L, 11L);

        assertThat(service(100, 10).diagnoseNew(NOW).requested()).isEqualTo(1);
        assertThat(saved).singleElement().satisfies(memo -> assertThat(memo.getHistoryMark()).isEqualTo(11L));
    }

    @Test
    @DisplayName("앞 50건(첫 페이지)이 모두 메모가 있으면 다음 페이지를 읽어 51번째를 진단한다. 페이지마다 이력 · 요청 상태는 한 번씩만 읽는다")
    void reachesNewTargetBehindRequestedOnes() {
        List<Long> orderIds = LongStream.rangeClosed(1, SCAN + 1).boxed().toList();
        stuck(orderIds);
        orderIds.subList(0, SCAN).forEach(orderId -> requested.add(orderId + ":" + orderId * 10));

        StuckOrderDiagnosisService.Run run = service(100, 10).diagnoseNew(NOW);

        assertThat(run.requested()).isEqualTo(1);
        assertThat(saved).singleElement().satisfies(memo -> assertThat(memo.getOrderId()).isEqualTo(SCAN + 1L));
        verify(historyRepository, times(1)).findAllByOrderIdOrderByOccurredAtAscIdAsc(anyLong());
        verify(historyRepository, times(2)).findLastHistoryIds(anyCollection());
        verify(repository, times(2)).findRequestedStates(eq(KIND), anyCollection());
    }

    @Test
    @DisplayName("페이지 상한(max-scan-pages)까지만 읽는다")
    void scanPageLimit() {
        List<Long> orderIds = LongStream.rangeClosed(1, SCAN + 1).boxed().toList();
        stuck(orderIds);
        orderIds.subList(0, SCAN).forEach(orderId -> requested.add(orderId + ":" + orderId * 10));

        assertThat(service(100, 10, 1).diagnoseNew(NOW).requested()).isZero();
        verify(stuckOrderService, never()).findStuckOrders(eq(KIND), any(), eq(1), anyInt());
    }

    @Test
    @DisplayName("하루 상한: 오늘(0시부터) 요청 수가 상한이면 부르지 않고, 남은 만큼만 부른다")
    void dailyLimit() {
        stuck(1L, 2L, 3L);
        when(repository.countByRequestedAtGreaterThanEqual(NOW.toLocalDate().atStartOfDay())).thenReturn(5L);

        assertThat(service(5, 10).diagnoseNew(NOW)).isEqualTo(new StuckOrderDiagnosisService.Run(0, 0, 0, true, false));
        verify(diagnoser, never()).diagnose(any());

        StuckOrderDiagnosisService.Run run = service(7, 10).diagnoseNew(NOW);
        assertThat(run.requested()).isEqualTo(2);
        assertThat(run.limitReached()).isTrue();
        verify(diagnoser, times(2)).diagnose(any());
    }

    @Test
    @DisplayName("한 실행 상한(max-per-run)을 넘지 않는다")
    void maxPerRun() {
        stuck(1L, 2L, 3L);

        StuckOrderDiagnosisService.Run run = service(100, 2).diagnoseNew(NOW);

        assertThat(run.requested()).isEqualTo(2);
        assertThat(run.limitReached()).isFalse();
    }

    @Test
    @DisplayName("실패(시간 초과)는 FAILED 메모로 남기고 계속한다. 구현이 던지거나 약속을 어긴 결과(성공인데 확신도 · 요약 없음, "
        + "제공자 · 모델 · 이유 없음)도 실패로 바꿔 실행이 멈추지 않는다")
    void failuresAndBrokenResultsAreRecordedAndContinue() {
        stuck(1L, 2L, 3L, 4L, 5L, 6L);
        when(diagnoser.diagnose(any()))
            .thenReturn(Diagnosis.failed("TIMEOUT", "api.groq.com", "m"))
            .thenThrow(new IllegalStateException("boom"))
            .thenReturn(new Diagnosis(StuckOrderDiagnoser.Outcome.DIAGNOSED, "요약", "조치", null, null, "p", "m"))
            .thenReturn(new Diagnosis(StuckOrderDiagnoser.Outcome.DIAGNOSED, " ", "조치",
                StuckOrderDiagnoser.Confidence.LOW, null, "p", "m"))
            .thenReturn(new Diagnosis(StuckOrderDiagnoser.Outcome.FAILED, null, null, null, null, null, null))
            .thenReturn(Diagnosis.diagnosed("요약", "조치", StuckOrderDiagnoser.Confidence.LOW, null, null));

        StuckOrderDiagnosisService.Run run = service(100, 10).diagnoseNew(NOW);

        assertThat(run).isEqualTo(new StuckOrderDiagnosisService.Run(6, 1, 5, false, false));
        assertThat(saved).extracting(StuckOrderDiagnosis::getStatus, StuckOrderDiagnosis::getFailureReason,
                StuckOrderDiagnosis::getProvider)
            .containsExactly(tuple(StuckOrderDiagnosis.Status.FAILED, "TIMEOUT", "api.groq.com"),
                tuple(StuckOrderDiagnosis.Status.FAILED, "ERROR", "unknown"),
                tuple(StuckOrderDiagnosis.Status.FAILED, "BAD_RESPONSE", "p"),
                tuple(StuckOrderDiagnosis.Status.FAILED, "BAD_RESPONSE", "p"),
                tuple(StuckOrderDiagnosis.Status.FAILED, "ERROR", "unknown"),
                tuple(StuckOrderDiagnosis.Status.DIAGNOSED, null, "unknown"));
    }

    @Test
    @DisplayName("서킷 열림 등으로 부르지 못하면 기록 없이 이번 실행을 멈춘다(다음 실행에서 다시 본다)")
    void stopsWhenNotCalled() {
        stuck(1L, 2L);
        when(diagnoser.diagnose(any())).thenReturn(Diagnosis.notCalled("CIRCUIT_OPEN"));

        StuckOrderDiagnosisService.Run run = service(100, 10).diagnoseNew(NOW);

        assertThat(run).isEqualTo(new StuckOrderDiagnosisService.Run(0, 0, 0, false, true));
        assertThat(saved).isEmpty();
        verify(diagnoser, times(1)).diagnose(any());
    }

    @Test
    @DisplayName("꺼져 있으면(스위치 · 키 없음) 멈춘 주문을 읽지도 부르지도 않는다")
    void doesNothingWhenUnavailable() {
        when(diagnoser.isAvailable()).thenReturn(false);

        assertThat(service(100, 10).diagnoseNew(NOW)).isEqualTo(new StuckOrderDiagnosisService.Run(0, 0, 0, false,
            false));
        verify(stuckOrderService, never()).findStuckOrders(anyString(), any(), anyInt(), anyInt());
        verify(diagnoser, never()).diagnose(any());
    }

    @Test
    @DisplayName("다른 서버가 같은 상태를 먼저 저장했으면(유니크 충돌) 실패 없이 넘어간다. 관련 주문이 없는 대상은 건너뛴다")
    void ignoresRaceAndOrderlessTargets() {
        when(stuckOrderService.findStuckOrders(eq(KIND), eq(NOW), eq(0), eq(SCAN))).thenReturn(new PageImpl<>(List.of(
            new StuckOrderResult(KIND, null, "ORDER_OUTBOX", 9L, "UNSENT", NOW),
            new StuckOrderResult(KIND, 1L, "ORDER", 1L, "PAID", NOW))));
        history(1L, 10L);
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("uk"));

        assertThat(service(100, 10).diagnoseNew(NOW).requested()).isEqualTo(1);
    }

    private static OrderStatusHistoryRepository.LastHistory lastHistory(Long orderId, Long lastHistoryId) {
        return new OrderStatusHistoryRepository.LastHistory() {
            @Override
            public Long getOrderId() {
                return orderId;
            }

            @Override
            public Long getLastHistoryId() {
                return lastHistoryId;
            }
        };
    }

    private static StuckOrderDiagnosisRepository.RequestedState requestedState(Long orderId, long historyMark) {
        return new StuckOrderDiagnosisRepository.RequestedState() {
            @Override
            public Long getOrderId() {
                return orderId;
            }

            @Override
            public long getHistoryMark() {
                return historyMark;
            }
        };
    }
}
