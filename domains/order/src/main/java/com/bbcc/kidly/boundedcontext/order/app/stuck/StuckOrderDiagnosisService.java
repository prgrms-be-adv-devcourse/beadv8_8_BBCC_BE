package com.bbcc.kidly.boundedcontext.order.app.stuck;

import static com.bbcc.kidly.boundedcontext.order.app.OrderTextGuard.codeOrNull;
import static com.bbcc.kidly.boundedcontext.order.app.OrderTextGuard.exceptionClassOnly;
import static com.bbcc.kidly.boundedcontext.order.app.OrderTextGuard.failureCodeOrNull;
import static com.bbcc.kidly.boundedcontext.order.app.OrderTextGuard.sourceOrNull;

import com.bbcc.kidly.boundedcontext.order.app.config.StuckOrderDiagnosisProperties;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventFailureRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderStatusHistoryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.PaymentRecoveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnoser;
import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnoser.Diagnosis;
import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnosisRepository;
import com.bbcc.kidly.boundedcontext.order.app.stuck.dto.StuckOrderResult;
import com.bbcc.kidly.boundedcontext.order.domain.history.OrderStatusHistory;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.OrderEventFailure;
import com.bbcc.kidly.boundedcontext.order.domain.stuck.StuckOrderDiagnosis;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

/**
 * 멈춘 주문 AI 진단 메모(#926). 주기 작업(in/scheduler)이 주문 처리와 다른 스레드 · 트랜잭션 밖에서 부른다.
 * 주문 · 결제 · 취소 흐름은 이 서비스를 부르지 않는다.
 * <ul>
 *   <li>멈춘 대상은 관리자 조회와 같은 {@link StuckOrderService}로 찾는다(종류마다 오래된 것부터 scan-size씩, 요청 상한이 남으면
 *       다음 페이지를 max-scan-pages까지. 앞쪽이 모두 이미 요청한 건이어도 뒤쪽 새 건에 닿는다)</li>
 *   <li>새 건만: (주문, 종류, 마지막 상태 이력 ID)로 이미 요청했으면(성공 · 실패 모두) 건너뛴다 → 상태가 바뀌기 전까지 한 번</li>
 *   <li>하루 상한(DB의 오늘 요청 수) · 한 실행 상한 안에서만 부른다</li>
 *   <li>실패 · 시간 초과 · 형식 오류는 FAILED 메모 한 줄로 남기고 넘어간다. 부르지 못했으면(꺼짐 · 서킷 열림) 기록 없이 이번 실행을 끝낸다</li>
 *   <li>AI 요청에 넣는 글 칸은 {@link com.bbcc.kidly.boundedcontext.order.app.OrderTextGuard}로 거른다(#925 MCP와 같은 규칙):
 *       상태 · 재고 사유는 대문자 코드, 이력 사유 · 실패 코드는 실패 코드 형식, 이력 출처 · 아웃박스 eventType은 경로 틀 ·
 *       scheduler · 이벤트 타입, 아웃박스 마지막 전송 실패는 예외 클래스 이름만. 형식이 아니면 null(프롬프트에서 빠진다)</li>
 *   <li>AI 호출은 트랜잭션 밖이다. 메모 저장만 자기 트랜잭션. 이 작업이 실패해도 멈춘 주문 처리 · 관리자 API는 그대로다</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StuckOrderDiagnosisService {

    // AI에 보내는 이력 · 기록 줄 수 상한(최근 것)
    static final int HISTORY_LINES = 20;
    private static final String UNKNOWN = "unknown";

    private final StuckOrderService stuckOrderService;
    private final StuckOrderDiagnoser diagnoser;
    private final StuckOrderDiagnosisRepository diagnosisRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OrderEventFailureRepository failureRepository;
    private final PaymentRecoveryRepository recoveryRepository;
    private final StuckOrderDiagnosisProperties properties;

    /**
     * 새로 멈춘 대상을 진단한다. 종류마다 오래된 것부터 scan-size씩 읽고, 요청 상한이 남아 있으면 다음 페이지를 읽는다
     * (max-scan-pages까지). 한 페이지의 마지막 이력 ID · 이미 요청한 상태는 쿼리 한 번씩으로 거른다(이미 메모가 있는 건은 추가 쿼리 없음).
     *
     * @param now 멈춤 판단 · 하루 시작 기준 시각
     */
    public Run diagnoseNew(LocalDateTime now) {
        if (!diagnoser.isAvailable()) {
            return Run.EMPTY;
        }
        long usedToday = diagnosisRepository.countByRequestedAtGreaterThanEqual(now.toLocalDate().atStartOfDay());
        int budget = (int) Math.min(properties.maxPerRun(), Math.max(0, properties.dailyLimit() - usedToday));
        Counter counter = new Counter();
        if (budget == 0) {
            counter.limitReached = usedToday >= properties.dailyLimit();
            return counter.toRun();
        }
        for (StuckOrderKind kind : StuckOrderKind.values()) {
            for (int page = 0; page < properties.maxScanPages(); page++) {
                Page<StuckOrderResult> rows = stuckOrderService.findStuckOrders(kind.name(), now, page,
                    properties.scanSize());
                for (StuckOrderResult stuck : newTargets(kind, rows.getContent())) {
                    if (counter.requested >= budget) {
                        counter.limitReached = usedToday + counter.requested >= properties.dailyLimit();
                        return counter.toRun();
                    }
                    if (!diagnose(stuck, counter)) {
                        return counter.toRun();
                    }
                }
                if (!rows.hasNext()) {
                    break;
                }
            }
        }
        return counter.toRun();
    }

    // 관련 주문이 있고, (주문, 종류, 마지막 이력 ID)로 아직 요청하지 않은 대상만(페이지당 쿼리 두 번)
    private List<StuckOrderResult> newTargets(StuckOrderKind kind, List<StuckOrderResult> rows) {
        List<Long> orderIds = rows.stream().map(StuckOrderResult::orderId).filter(Objects::nonNull).distinct()
            .toList();
        if (orderIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> marks = new HashMap<>();
        historyRepository.findLastHistoryIds(orderIds)
            .forEach(row -> marks.put(row.getOrderId(), row.getLastHistoryId()));
        Set<String> requested = new HashSet<>();
        diagnosisRepository.findRequestedStates(kind.name(), orderIds)
            .forEach(row -> requested.add(row.getOrderId() + ":" + row.getHistoryMark()));
        return rows.stream()
            .filter(row -> row.orderId() != null)
            .filter(row -> !requested.contains(row.orderId() + ":" + marks.getOrDefault(row.orderId(), 0L)))
            .toList();
    }

    // 부르지 못했으면(꺼짐 · 서킷 열림) false: 이번 실행은 여기서 멈춘다
    private boolean diagnose(StuckOrderResult stuck, Counter counter) {
        List<OrderStatusHistory> history = historyRepository.findAllByOrderIdOrderByOccurredAtAscIdAsc(stuck.orderId());
        // 중복 판단과 같은 값: 가장 큰 이력 ID(없으면 0)
        long mark = history.stream().mapToLong(OrderStatusHistory::getId).max().orElse(0L);
        StuckOrderDiagnosis.Key key = new StuckOrderDiagnosis.Key(stuck.orderId(), stuck.kind(), mark);
        LocalDateTime requestedAt = LocalDateTime.now();
        Diagnosis diagnosis = call(requestOf(stuck, history));
        if (diagnosis.outcome() == StuckOrderDiagnoser.Outcome.NOT_CALLED) {
            log.info("멈춘 주문 진단 이번 실행 중단: reason={}", diagnosis.reason());
            counter.stopped = true;
            return false;
        }
        counter.requested++;
        StuckOrderDiagnosis.Source source = new StuckOrderDiagnosis.Source(
            diagnosis.provider(), diagnosis.model(), requestedAt);
        StuckOrderDiagnosis memo;
        if (diagnosis.outcome() == StuckOrderDiagnoser.Outcome.DIAGNOSED) {
            counter.diagnosed++;
            memo = StuckOrderDiagnosis.diagnosed(key, diagnosis.summary(), diagnosis.recommendedAction(),
                StuckOrderDiagnosis.Confidence.valueOf(diagnosis.confidence().name()), source);
        } else {
            counter.failed++;
            memo = StuckOrderDiagnosis.failed(key, diagnosis.reason(), source);
        }
        save(memo);
        return true;
    }

    // 구현이 약속을 어겨도(던짐 · 빈 결과 · 성공인데 칸 빠짐 · 제공자/모델/이유 없음) 실패 메모로 바꿔 실행 전체가 멈추지 않게 한다
    private Diagnosis call(StuckOrderDiagnoser.Request request) {
        Diagnosis diagnosis;
        try {
            diagnosis = diagnoser.diagnose(request);
        } catch (RuntimeException e) {
            log.warn("멈춘 주문 진단 실패: kind={} error={}", request.stuckKind(), e.getClass().getSimpleName());
            return Diagnosis.failed("ERROR", UNKNOWN, UNKNOWN);
        }
        if (diagnosis == null || diagnosis.outcome() == null) {
            return Diagnosis.failed("ERROR", UNKNOWN, UNKNOWN);
        }
        if (diagnosis.outcome() == StuckOrderDiagnoser.Outcome.NOT_CALLED) {
            return diagnosis;
        }
        String provider = orUnknown(diagnosis.provider());
        String model = orUnknown(diagnosis.model());
        if (diagnosis.outcome() == StuckOrderDiagnoser.Outcome.DIAGNOSED) {
            if (isBlank(diagnosis.summary()) || isBlank(diagnosis.recommendedAction())
                || diagnosis.confidence() == null) {
                log.warn("멈춘 주문 진단 결과 형식 오류: kind={} provider={} model={}", request.stuckKind(), provider, model);
                return Diagnosis.failed("BAD_RESPONSE", provider, model);
            }
            return Diagnosis.diagnosed(diagnosis.summary(), diagnosis.recommendedAction(), diagnosis.confidence(),
                provider, model);
        }
        return Diagnosis.failed(isBlank(diagnosis.reason()) ? "ERROR" : diagnosis.reason(), provider, model);
    }

    private static String orUnknown(String value) {
        return isBlank(value) ? UNKNOWN : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // 다른 서버가 같은 상태를 먼저 저장했으면(유니크) 그대로 둔다
    private void save(StuckOrderDiagnosis memo) {
        try {
            diagnosisRepository.save(memo);
        } catch (DataIntegrityViolationException e) {
            log.debug("같은 상태의 진단 메모가 이미 있습니다: orderId={} kind={}", memo.getOrderId(), memo.getStuckKind());
        }
    }

    private StuckOrderDiagnoser.Request requestOf(StuckOrderResult stuck, List<OrderStatusHistory> history) {
        List<StuckOrderDiagnoser.HistoryLine> lines = history.subList(Math.max(0, history.size() - HISTORY_LINES),
                history.size()).stream()
            .map(row -> new StuckOrderDiagnoser.HistoryLine(row.getTargetType().name(),
                codeOrNull(row.getFromStatus()), codeOrNull(row.getToStatus()), failureCodeOrNull(row.getReason()),
                row.getActorType().name(), sourceOrNull(row.getSource()), row.getOccurredAt()))
            .toList();
        String orderStatus = history.stream()
            .filter(row -> row.getTargetType() == OrderStatusHistory.TargetType.ORDER)
            .reduce((first, second) -> second)
            .map(row -> codeOrNull(row.getToStatus()))
            .orElse(null);
        return new StuckOrderDiagnoser.Request(stuck.kind(), stuck.targetType(), stuck.status(), stuck.since(),
            orderStatus, lines, recordsOf(stuck));
    }

    private List<StuckOrderDiagnoser.RecordLine> recordsOf(StuckOrderResult stuck) {
        List<StuckOrderDiagnoser.RecordLine> records = new ArrayList<>();
        Arrays.stream(OrderEventFailure.EventType.values())
            .flatMap(type -> failureRepository.findByOrderIdAndEventType(stuck.orderId(), type).stream())
            .forEach(failure -> records.add(new StuckOrderDiagnoser.RecordLine("EVENT_FAILURE",
                failure.getEventType().name(), failure.getStatus().name(), failureCodeOrNull(failure.getFailureCode()),
                failure.getAttemptCount())));
        recoveryRepository.findByOrderId(stuck.orderId()).ifPresent(recovery -> records.add(
            new StuckOrderDiagnoser.RecordLine("PAYMENT_RECOVERY", "payment.completed", recovery.getStatus().name(),
                failureCodeOrNull(recovery.getFailureCode()), recovery.getAttemptCount())));
        if (stuck.outbox() != null) {
            // 마지막 전송 실패는 예외 클래스 이름만(메시지는 내보내지 않는다)
            records.add(new StuckOrderDiagnoser.RecordLine("OUTBOX", sourceOrNull(stuck.outbox().eventType()),
                stuck.outbox().quarantinedAt() != null ? "QUARANTINED" : "UNSENT",
                exceptionClassOnly(stuck.outbox().lastError()), stuck.outbox().attemptCount()));
        }
        return records;
    }

    /**
     * 한 실행 결과.
     *
     * @param requested 실제로 요청한 수(성공 + 실패)
     * @param limitReached 하루 상한에 닿았는지
     * @param stopped 부르지 못해(서킷 열림 등) 중간에 멈췄는지
     */
    public record Run(int requested, int diagnosed, int failed, boolean limitReached, boolean stopped) {

        static final Run EMPTY = new Run(0, 0, 0, false, false);
    }

    private static final class Counter {

        private int requested;
        private int diagnosed;
        private int failed;
        private boolean limitReached;
        private boolean stopped;

        private Run toRun() {
            return new Run(requested, diagnosed, failed, limitReached, stopped);
        }
    }
}
