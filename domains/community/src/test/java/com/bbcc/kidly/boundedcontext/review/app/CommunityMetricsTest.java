package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.review.app.CommunityMetrics.SchedulerTask;
import com.bbcc.kidly.boundedcontext.review.app.CommunityMetrics.StorageOperation;
import com.bbcc.kidly.boundedcontext.review.app.ReplicaHealthService.ReplicaHealth;
import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters;
import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters.State;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.FailureCause;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Result;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewModerationStats;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewModerationStats.StatusCountRow;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationReason;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationStatus;
import com.bbcc.kidly.boundedcontext.support.MutableTestClock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 커뮤니티 운영 지표: 이름 · 종류 · 라벨(고정 집합) · 값 출처. 게이지는 운영 조회와 같은 app 조회로 읽고, 검사 대기 세 값은 한 번 읽은 묶음을
 * 짧게 캐시한다. 시각은 epoch 초이고 시계를 바꿔 끼워 확인한다.
 */
class CommunityMetricsTest {

    private static final ZoneId ZONE = MutableTestClock.ZONE;
    private static final Instant START = Instant.parse("2026-11-09T01:00:00Z");
    private static final int MAX_ATTEMPTS = 3;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final CommunityDeadLetters deadLetters = mock(CommunityDeadLetters.class);
    private final ReviewModerationStats moderationStats = mock(ReviewModerationStats.class);
    private final ReplicaHealthService replicaHealth = mock(ReplicaHealthService.class);
    private final MutableTestClock clock = new MutableTestClock(START);
    private final CommunityMetrics metrics = new CommunityMetrics(registry, deadLetters, moderationStats,
        new ReviewModerationProperties(false, Duration.ofMinutes(1), 10, MAX_ATTEMPTS), replicaHealth, clock);

    @Test
    @DisplayName("DLT: 미처리(OPEN) 수 · 가장 오래된 미처리의 나이(없으면 0)")
    void deadLetterGauges() {
        when(deadLetters.countByState(State.OPEN)).thenReturn(3L);
        when(deadLetters.findOldestCreatedAt(State.OPEN)).thenReturn(null);

        assertThat(gauge(CommunityMetrics.DLT_MESSAGES, "state", "open")).isEqualTo(3.0);
        assertThat(gauge(CommunityMetrics.DLT_OLDEST_AGE)).isZero();

        when(deadLetters.findOldestCreatedAt(State.OPEN)).thenReturn(local(START.minusSeconds(120)));
        assertThat(gauge(CommunityMetrics.DLT_OLDEST_AGE)).isEqualTo(120.0);
    }

    @Test
    @DisplayName("DLT 마지막 보관 시각은 처음 보관 전 NaN, 보관하면 그 시각(epoch 초). 보관 실패는 카운터만 는다")
    void deadLetterStoreRecords() {
        assertThat(gauge(CommunityMetrics.DLT_LAST_STORED)).isNaN();
        assertThat(counter(CommunityMetrics.DLT_STORE_FAILURES)).isZero();

        metrics.recordDeadLetterStoreFailure();
        assertThat(counter(CommunityMetrics.DLT_STORE_FAILURES)).isEqualTo(1.0);
        assertThat(gauge(CommunityMetrics.DLT_LAST_STORED)).isNaN();

        clock.set(START.plusSeconds(30));
        metrics.recordDeadLetterStored();
        assertThat(gauge(CommunityMetrics.DLT_LAST_STORED)).isEqualTo(START.plusSeconds(30).getEpochSecond());
    }

    @Test
    @DisplayName("검사 대기: waiting(시도 0) · retrying(시도 ≥1, 상한 전) · exhausted(상한 도달). 세 값은 한 번 읽은 묶음을 캐시한다")
    void moderationPendingStages() {
        StatusCountRow pending = statusRow(ModerationStatus.PENDING, 10);
        when(moderationStats.countByModerationStatus()).thenReturn(List.of(pending));
        when(moderationStats.countRetryingPending(MAX_ATTEMPTS)).thenReturn(3L);
        when(moderationStats.countExhaustedPending(MAX_ATTEMPTS)).thenReturn(2L);

        assertThat(gauge(CommunityMetrics.MODERATION_PENDING, "stage", "waiting")).isEqualTo(5.0);
        assertThat(gauge(CommunityMetrics.MODERATION_PENDING, "stage", "retrying")).isEqualTo(3.0);
        assertThat(gauge(CommunityMetrics.MODERATION_PENDING, "stage", "exhausted")).isEqualTo(2.0);
        verify(moderationStats, times(1)).countByModerationStatus();
        verify(moderationStats, times(1)).countRetryingPending(MAX_ATTEMPTS);
        verify(moderationStats, times(1)).countExhaustedPending(MAX_ATTEMPTS);

        // 캐시 시간이 지나면 다시 읽는다
        clock.set(START.plusSeconds(11));
        when(moderationStats.countExhaustedPending(MAX_ATTEMPTS)).thenReturn(4L);
        assertThat(gauge(CommunityMetrics.MODERATION_PENDING, "stage", "exhausted")).isEqualTo(4.0);
        assertThat(gauge(CommunityMetrics.MODERATION_PENDING, "stage", "waiting")).isEqualTo(3.0);
        verify(moderationStats, times(2)).countByModerationStatus();
    }

    @Test
    @DisplayName("검사 대기 가장 오래된 나이: 상한 전 대기의 작성 시각 기준, 없으면 0")
    void moderationOldestAge() {
        when(moderationStats.findOldestPendingCreatedAt(MAX_ATTEMPTS)).thenReturn(null);
        assertThat(gauge(CommunityMetrics.MODERATION_PENDING_OLDEST_AGE)).isZero();

        when(moderationStats.findOldestPendingCreatedAt(MAX_ATTEMPTS)).thenReturn(local(START.minusSeconds(1800)));
        assertThat(gauge(CommunityMetrics.MODERATION_PENDING_OLDEST_AGE)).isEqualTo(1800.0);
    }

    @Test
    @DisplayName("검사기 호출 결과 카운터: 라벨은 result(passed · flagged · undecided) · cause(none · unavailable · timeout · "
        + "rate_limited)뿐이고, 다섯 조합이 처음부터 0으로 있다")
    void moderationChecks() {
        assertThat(registry.find(CommunityMetrics.MODERATION_CHECKS).counters()).hasSize(5)
            .allSatisfy(counter -> assertThat(counter.count()).isZero());

        metrics.recordModerationCheck(Result.passed());
        metrics.recordModerationCheck(Result.flagged(ModerationReason.HATE));
        metrics.recordModerationCheck(Result.undecided(FailureCause.RATE_LIMITED));
        metrics.recordModerationCheck(Result.undecided(FailureCause.TIMEOUT));
        metrics.recordModerationCheck(Result.undecided(FailureCause.UNAVAILABLE));
        metrics.recordModerationCheck(Result.undecided(FailureCause.UNAVAILABLE));

        assertThat(counter(CommunityMetrics.MODERATION_CHECKS, "result", "passed", "cause", "none")).isEqualTo(1.0);
        assertThat(counter(CommunityMetrics.MODERATION_CHECKS, "result", "flagged", "cause", "none")).isEqualTo(1.0);
        assertThat(counter(CommunityMetrics.MODERATION_CHECKS, "result", "undecided", "cause", "rate_limited"))
            .isEqualTo(1.0);
        assertThat(counter(CommunityMetrics.MODERATION_CHECKS, "result", "undecided", "cause", "timeout"))
            .isEqualTo(1.0);
        assertThat(counter(CommunityMetrics.MODERATION_CHECKS, "result", "undecided", "cause", "unavailable"))
            .isEqualTo(2.0);
        assertThat(registry.find(CommunityMetrics.MODERATION_CHECKS).counters()).hasSize(5);
    }

    @Test
    @DisplayName("스케줄러 마지막 성공 시각: 등록한(켜진) 스케줄러만 있고 처음 값은 등록(기동) 시각, 성공하면 그 시각")
    void schedulerLastSuccess() {
        assertThat(registry.find(CommunityMetrics.SCHEDULER_LAST_SUCCESS).gauges()).isEmpty();
        metrics.recordSchedulerSuccess(SchedulerTask.REVIEW_MODERATION);
        assertThat(registry.find(CommunityMetrics.SCHEDULER_LAST_SUCCESS).gauges()).isEmpty();

        metrics.registerScheduler(SchedulerTask.REVIEW_MODERATION);
        assertThat(gauge(CommunityMetrics.SCHEDULER_LAST_SUCCESS, "task", "review_moderation"))
            .isEqualTo(START.getEpochSecond());

        clock.set(START.plusSeconds(60));
        metrics.recordSchedulerSuccess(SchedulerTask.REVIEW_MODERATION);
        assertThat(gauge(CommunityMetrics.SCHEDULER_LAST_SUCCESS, "task", "review_moderation"))
            .isEqualTo(START.plusSeconds(60).getEpochSecond());
        assertThat(registry.find(CommunityMetrics.SCHEDULER_LAST_SUCCESS).tag("task", "review_image_cleanup")
            .gauges()).isEmpty();
    }

    @Test
    @DisplayName("사진 저장소 확인 불가 카운터: operation=upload_check 하나가 처음부터 0으로 있다")
    void imageStorageUnavailable() {
        assertThat(counter(CommunityMetrics.IMAGE_STORAGE_UNAVAILABLE, "operation", "upload_check")).isZero();

        metrics.recordImageStorageUnavailable(StorageOperation.UPLOAD_CHECK);

        assertThat(counter(CommunityMetrics.IMAGE_STORAGE_UNAVAILABLE, "operation", "upload_check")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("상품 복사본 마지막 반영 뒤 지난 시간: 복사본이 없으면 NaN")
    void replicaAge() {
        when(replicaHealth.health()).thenReturn(new ReplicaHealth(0, Map.of(), null, null));
        assertThat(gauge(CommunityMetrics.REPLICA_LAST_CHANGED_AGE)).isNaN();

        when(replicaHealth.health()).thenReturn(new ReplicaHealth(2, Map.of(), local(START.minusSeconds(300)), null));
        assertThat(gauge(CommunityMetrics.REPLICA_LAST_CHANGED_AGE)).isEqualTo(300.0);
    }

    @Test
    @DisplayName("게이지 조회가 실패하면 NaN(수집을 깨지 않는다)")
    void gaugeFailureIsNaN() {
        when(deadLetters.countByState(State.OPEN)).thenThrow(new IllegalStateException("db down"));

        assertThat(gauge(CommunityMetrics.DLT_MESSAGES, "state", "open")).isNaN();
    }

    @Test
    @DisplayName("모든 지표 이름은 kidly.community.로 시작하고, 라벨 값은 고정 집합 안에 있다")
    void fixedLabels() {
        metrics.registerScheduler(SchedulerTask.REVIEW_MODERATION);
        metrics.registerScheduler(SchedulerTask.REVIEW_IMAGE_CLEANUP);
        Map<String, Set<String>> allowed = Map.of(
            "state", Set.of("open"),
            "stage", Set.of("waiting", "retrying", "exhausted"),
            "result", Set.of("passed", "flagged", "undecided"),
            "cause", Set.of("none", "unavailable", "timeout", "rate_limited"),
            "task", Set.of("review_moderation", "review_image_cleanup"),
            "operation", Set.of("upload_check"));

        List<Meter> meters = registry.getMeters();
        assertThat(meters.stream().map(meter -> meter.getId().getName()).collect(Collectors.toSet()))
            .containsExactlyInAnyOrder(CommunityMetrics.DLT_MESSAGES, CommunityMetrics.DLT_OLDEST_AGE,
                CommunityMetrics.DLT_STORE_FAILURES, CommunityMetrics.DLT_LAST_STORED,
                CommunityMetrics.MODERATION_PENDING, CommunityMetrics.MODERATION_PENDING_OLDEST_AGE,
                CommunityMetrics.MODERATION_CHECKS, CommunityMetrics.SCHEDULER_LAST_SUCCESS,
                CommunityMetrics.IMAGE_STORAGE_UNAVAILABLE, CommunityMetrics.REPLICA_LAST_CHANGED_AGE);
        for (Meter meter : meters) {
            for (Tag tag : meter.getId().getTags()) {
                assertThat(allowed).as(meter.getId().toString()).containsKey(tag.getKey());
                assertThat(allowed.get(tag.getKey())).as(meter.getId().toString()).contains(tag.getValue());
            }
        }
    }

    private double gauge(String name, String... tags) {
        return registry.get(name).tags(tags).gauge().value();
    }

    private double counter(String name, String... tags) {
        return registry.get(name).tags(tags).counter().count();
    }

    private static LocalDateTime local(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZONE);
    }

    private static StatusCountRow statusRow(ModerationStatus status, long total) {
        return new StatusCountRow() {
            @Override
            public ModerationStatus getStatus() {
                return status;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
