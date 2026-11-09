package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.app.ReplicaHealthService.ReplicaHealth;
import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters;
import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters.State;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.FailureCause;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Result;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Verdict;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewModerationStats;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewModerationStats.StatusCountRow;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 커뮤니티 운영 지표. 이름 · 라벨 값은 이 클래스에만 두고, 라벨 값은 고정 집합뿐이다(리뷰 · 회원 · 상품 ID · 닉네임을 넣지 않는다).
 * in(리스너 · 스케줄러)과 app 서비스는 기록 메서드만 부른다.
 * <ul>
 *   <li>게이지는 운영 조회(MCP)와 같은 app 조회로 수집할 때마다 읽는다. 검사 대기 세 값(stage)은 한 번 읽은 묶음을 10초 캐시해
 *       같은 수집에서 쿼리가 세 번 나가지 않게 한다. 조회가 실패하면 NaN(수집을 깨지 않는다)</li>
 *   <li>카운터는 그 경로에서만 는다: DLT 보관 실패(재시도마다), 검사기 호출 결과(호출마다, 반영 여부와 상관없다), 사진 업로드 확인 불가(503)</li>
 *   <li>시각은 epoch 초. 저장 시각(LocalDateTime)은 시계의 시간대로 바꾼다. 스케줄러 마지막 성공은 켜진 스케줄러만 등록하고 처음 값은
 *       등록(기동) 시각이다(한 번도 성공하지 못해도 알림에 잡히게). DLT 마지막 보관은 처음 보관 전 NaN</li>
 * </ul>
 */
@Slf4j
@Component
public class CommunityMetrics {

    public static final String DLT_MESSAGES = "kidly.community.dlt.messages";
    public static final String DLT_OLDEST_AGE = "kidly.community.dlt.oldest.age.seconds";
    public static final String DLT_STORE_FAILURES = "kidly.community.dlt.store.failures";
    public static final String DLT_LAST_STORED = "kidly.community.dlt.last.stored.seconds";
    public static final String MODERATION_PENDING = "kidly.community.moderation.pending";
    public static final String MODERATION_PENDING_OLDEST_AGE = "kidly.community.moderation.pending.oldest.age.seconds";
    public static final String MODERATION_CHECKS = "kidly.community.moderation.checks";
    public static final String SCHEDULER_LAST_SUCCESS = "kidly.community.scheduler.last.success.seconds";
    public static final String IMAGE_STORAGE_UNAVAILABLE = "kidly.community.review.image.storage.unavailable";
    public static final String REPLICA_LAST_CHANGED_AGE = "kidly.community.replica.product.last.changed.age.seconds";

    static final Duration PENDING_CACHE = Duration.ofSeconds(10);
    private static final String NONE = "none";

    private final MeterRegistry registry;
    private final CommunityDeadLetters deadLetters;
    private final ReviewModerationStats moderationStats;
    private final ReviewModerationProperties moderationProperties;
    private final ReplicaHealthService replicaHealth;
    private final Clock clock;

    private final AtomicReference<Double> lastDeadLetterStored = new AtomicReference<>(Double.NaN);
    private final Counter deadLetterStoreFailures;
    private final Map<Verdict, Map<FailureCause, Counter>> undecidedChecks = new EnumMap<>(Verdict.class);
    private final Map<Verdict, Counter> decidedChecks = new EnumMap<>(Verdict.class);
    private final Map<StorageOperation, Counter> storageUnavailable = new EnumMap<>(StorageOperation.class);
    private final Map<SchedulerTask, AtomicReference<Double>> schedulerLastSuccess = new EnumMap<>(SchedulerTask.class);
    private PendingSnapshot pendingSnapshot;

    @Autowired
    public CommunityMetrics(MeterRegistry registry, CommunityDeadLetters deadLetters,
        ReviewModerationStats moderationStats, ReviewModerationProperties moderationProperties,
        ReplicaHealthService replicaHealth) {
        this(registry, deadLetters, moderationStats, moderationProperties, replicaHealth, Clock.systemDefaultZone());
    }

    /**
     * @param clock "지금"과 저장 시각(LocalDateTime)의 시간대. 앱은 시스템 시계(저장 시각과 같은 시간대)
     */
    public CommunityMetrics(MeterRegistry registry, CommunityDeadLetters deadLetters,
        ReviewModerationStats moderationStats, ReviewModerationProperties moderationProperties,
        ReplicaHealthService replicaHealth, Clock clock) {
        this.registry = registry;
        this.deadLetters = deadLetters;
        this.moderationStats = moderationStats;
        this.moderationProperties = moderationProperties;
        this.replicaHealth = replicaHealth;
        this.clock = clock;

        gauge(Gauge.builder(DLT_MESSAGES, this, metrics -> metrics.read(() ->
            (double) deadLetters.countByState(State.OPEN))).tag("state", "open")
            .description("보관된 미처리(OPEN) DLT 수"));
        gauge(Gauge.builder(DLT_OLDEST_AGE, this, metrics -> metrics.read(() ->
            metrics.ageOrZero(deadLetters.findOldestCreatedAt(State.OPEN))))
            .description("가장 오래된 미처리 DLT의 나이(초), 없으면 0"));
        deadLetterStoreFailures = Counter.builder(DLT_STORE_FAILURES)
            .description("DLT 보관(DB 저장) 실패 횟수(재시도마다)").register(registry);
        gauge(Gauge.builder(DLT_LAST_STORED, lastDeadLetterStored, AtomicReference::get)
            .description("마지막 DLT 보관 성공 시각(epoch 초), 처음 보관 전 NaN"));

        pendingGauge("waiting", PendingSnapshot::waiting);
        pendingGauge("retrying", PendingSnapshot::retrying);
        pendingGauge("exhausted", PendingSnapshot::exhausted);
        gauge(Gauge.builder(MODERATION_PENDING_OLDEST_AGE, this, metrics -> metrics.read(() -> metrics.ageOrZero(
            moderationStats.findOldestPendingCreatedAt(moderationProperties.maxAttempts()))))
            .description("재시도 상한 전 검사 대기 중 가장 오래된 리뷰의 나이(작성 시각 기준, 초), 없으면 0"));

        decidedChecks.put(Verdict.PASSED, checkCounter("passed", NONE));
        decidedChecks.put(Verdict.FLAGGED, checkCounter("flagged", NONE));
        Map<FailureCause, Counter> undecided = new EnumMap<>(FailureCause.class);
        for (FailureCause cause : FailureCause.values()) {
            undecided.put(cause, checkCounter("undecided", causeTag(cause)));
        }
        undecidedChecks.put(Verdict.UNDECIDED, undecided);

        for (StorageOperation operation : StorageOperation.values()) {
            storageUnavailable.put(operation, Counter.builder(IMAGE_STORAGE_UNAVAILABLE)
                .tag("operation", operation.tag())
                .description("사진 저장소 확인 불가(503 IMAGE_STORAGE_UNAVAILABLE)로 끝난 횟수")
                .register(registry));
        }

        gauge(Gauge.builder(REPLICA_LAST_CHANGED_AGE, this, metrics -> metrics.read(() -> {
            ReplicaHealth health = replicaHealth.health();
            return health.total() == 0 || health.lastUpdatedAt() == null ? Double.NaN
                : metrics.ageOrZero(health.lastUpdatedAt());
        })).description("상품 복사본 마지막 반영 뒤 지난 시간(초), 복사본이 없으면 NaN"));
    }

    /**
     * DLT 보관이 끝났다(새로 넣음 · 이미 있음 모두, 보관 트랜잭션이 끝난 뒤).
     */
    public void recordDeadLetterStored() {
        lastDeadLetterStored.set(nowSeconds());
    }

    /**
     * DLT 보관이 실패했다(재시도마다 센다).
     */
    public void recordDeadLetterStoreFailure() {
        deadLetterStoreFailures.increment();
    }

    /**
     * 검사기(체인) 호출 결과. 호출마다 센다(그 결과가 반영되었는지와 상관없다).
     */
    public void recordModerationCheck(Result result) {
        if (result.verdict() == Verdict.UNDECIDED) {
            FailureCause cause = result.cause() == null ? FailureCause.UNAVAILABLE : result.cause();
            undecidedChecks.get(Verdict.UNDECIDED).get(cause).increment();
        } else {
            decidedChecks.get(result.verdict()).increment();
        }
    }

    /**
     * 켜진 스케줄러를 등록한다(처음 값은 지금 = 기동 시각). 꺼진 스케줄러는 부르지 않으므로 시계열이 없다.
     */
    public synchronized void registerScheduler(SchedulerTask task) {
        if (schedulerLastSuccess.containsKey(task)) {
            return;
        }
        AtomicReference<Double> lastSuccess = new AtomicReference<>(nowSeconds());
        schedulerLastSuccess.put(task, lastSuccess);
        gauge(Gauge.builder(SCHEDULER_LAST_SUCCESS, lastSuccess, AtomicReference::get).tag("task", task.tag())
            .description("그 스케줄러가 이 서버에서 끝까지 돈 마지막 시각(epoch 초), 처음 값은 기동 시각"));
    }

    /**
     * 스케줄러가 한 주기를 끝까지 돌았다(잠금을 못 잡아 건너뛴 주기 · 예외는 부르지 않는다). 등록하지 않은 스케줄러는 무시한다.
     */
    public void recordSchedulerSuccess(SchedulerTask task) {
        AtomicReference<Double> lastSuccess = schedulerLastSuccess.get(task);
        if (lastSuccess != null) {
            lastSuccess.set(nowSeconds());
        }
    }

    /**
     * 사진 저장소가 답하지 못해 요청이 503으로 끝났다.
     */
    public void recordImageStorageUnavailable(StorageOperation operation) {
        storageUnavailable.get(operation).increment();
    }

    /**
     * 검사 대기 묶음 캐시를 비운다(테스트가 값을 바꾼 뒤 바로 읽을 때).
     */
    synchronized void clearModerationPendingCache() {
        pendingSnapshot = null;
    }

    private void pendingGauge(String stage, ToLongFunction<PendingSnapshot> value) {
        gauge(Gauge.builder(MODERATION_PENDING, this,
                metrics -> metrics.read(() -> (double) value.applyAsLong(metrics.pendingSnapshot())))
            .tag("stage", stage)
            .description("검사 대기 수: waiting 시도 0 · retrying 시도 1 이상 상한 전 · exhausted 상한 도달"));
    }

    private synchronized PendingSnapshot pendingSnapshot() {
        Instant now = clock.instant();
        if (pendingSnapshot != null && now.isBefore(pendingSnapshot.readAt().plus(PENDING_CACHE))) {
            return pendingSnapshot;
        }
        int maxAttempts = moderationProperties.maxAttempts();
        long total = moderationStats.countByModerationStatus().stream()
            .filter(row -> row.getStatus() == ModerationStatus.PENDING)
            .mapToLong(StatusCountRow::getTotal)
            .sum();
        long retrying = moderationStats.countRetryingPending(maxAttempts);
        long exhausted = moderationStats.countExhaustedPending(maxAttempts);
        // 세 수는 따로 세므로 그 사이에 상태가 바뀌어도 음수가 되지 않게 한다
        pendingSnapshot = new PendingSnapshot(Math.max(0, total - retrying - exhausted), retrying, exhausted, now);
        return pendingSnapshot;
    }

    private Counter checkCounter(String result, String cause) {
        return Counter.builder(MODERATION_CHECKS).tag("result", result).tag("cause", cause)
            .description("검사기 호출 결과(호출마다 센다, 반영 여부와 상관없다)").register(registry);
    }

    private static String causeTag(FailureCause cause) {
        return switch (cause) {
            case UNAVAILABLE -> "unavailable";
            case TIMEOUT -> "timeout";
            case RATE_LIMITED -> "rate_limited";
        };
    }

    private void gauge(Gauge.Builder<?> builder) {
        builder.register(registry);
    }

    // 조회가 실패하면 NaN. 예외 메시지에 값이 들어갈 수 있어 예외 종류만 남긴다
    private double read(Supplier<Double> value) {
        try {
            return value.get();
        } catch (RuntimeException e) {
            log.warn("커뮤니티 지표를 읽지 못했습니다. error={}", e.getClass().getSimpleName());
            return Double.NaN;
        }
    }

    private double ageOrZero(LocalDateTime since) {
        if (since == null) {
            return 0;
        }
        double age = Duration.between(since.atZone(clock.getZone()).toInstant(), clock.instant()).toMillis() / 1000.0;
        return Math.max(0, age);
    }

    private double nowSeconds() {
        return clock.instant().toEpochMilli() / 1000.0;
    }

    private record PendingSnapshot(long waiting, long retrying, long exhausted, Instant readAt) {
    }

    /**
     * 지표에 남기는 주기 작업(라벨 task 값).
     */
    public enum SchedulerTask {
        REVIEW_MODERATION("review_moderation"),
        REVIEW_IMAGE_CLEANUP("review_image_cleanup");

        private final String tag;

        SchedulerTask(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    /**
     * 사진 저장소 작업(라벨 operation 값).
     */
    public enum StorageOperation {
        UPLOAD_CHECK("upload_check");

        private final String tag;

        StorageOperation(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }
}
