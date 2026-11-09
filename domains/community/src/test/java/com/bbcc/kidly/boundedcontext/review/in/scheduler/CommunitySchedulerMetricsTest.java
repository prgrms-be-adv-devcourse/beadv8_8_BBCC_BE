package com.bbcc.kidly.boundedcontext.review.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.review.app.CommunityMetrics;
import com.bbcc.kidly.boundedcontext.review.app.ReplicaHealthService;
import com.bbcc.kidly.boundedcontext.review.app.ReviewImageCleanupService;
import com.bbcc.kidly.boundedcontext.review.app.ReviewImageCleanupService.CleanupRun;
import com.bbcc.kidly.boundedcontext.review.app.ReviewImageCleanupService.PendingRun;
import com.bbcc.kidly.boundedcontext.review.app.ReviewImageCleanupService.SweepRun;
import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationProperties;
import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationService;
import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationService.ModerationRun;
import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewModerationStats;
import com.bbcc.kidly.boundedcontext.support.MutableTestClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 스케줄러 마지막 성공 시각: 만들어질 때(기동) 시각으로 시작하고, 끝까지 돈 주기에서만 바뀐다. 잠금을 못 잡아 건너뛴 주기 · 예외 ·
 * 저장소에 닿지 않는 설정은 그대로다.
 */
class CommunitySchedulerMetricsTest {

    private static final Instant START = Instant.parse("2026-11-09T01:00:00Z");
    private static final Instant LATER = START.plusSeconds(60);

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MutableTestClock clock = new MutableTestClock(START);
    private final CommunityMetrics metrics = new CommunityMetrics(registry, mock(CommunityDeadLetters.class),
        mock(ReviewModerationStats.class), new ReviewModerationProperties(false, Duration.ofMinutes(1), 10, 3),
        mock(ReplicaHealthService.class), clock);

    @Test
    @DisplayName("검사 스케줄러: 처음 값은 기동 시각, 끝까지 돌면 그 시각, 건너뜀 · 예외면 그대로")
    void moderation() {
        ReviewModerationService service = mock(ReviewModerationService.class);
        CommunityReviewModerationScheduler scheduler = new CommunityReviewModerationScheduler(service, metrics);
        assertThat(lastSuccess("review_moderation")).isEqualTo(START.getEpochSecond());

        clock.set(LATER);
        when(service.processPending()).thenReturn(new ModerationRun(0, 0, 0, 0, false, true));
        scheduler.moderate();
        assertThat(lastSuccess("review_moderation")).isEqualTo(START.getEpochSecond());

        when(service.processPending()).thenThrow(new IllegalStateException("db down"));
        scheduler.moderate();
        assertThat(lastSuccess("review_moderation")).isEqualTo(START.getEpochSecond());

        doReturn(new ModerationRun(1, 0, 0, 0, false, false)).when(service).processPending();
        scheduler.moderate();
        assertThat(lastSuccess("review_moderation")).isEqualTo(LATER.getEpochSecond());
    }

    @Test
    @DisplayName("사진 정리 스케줄러: 처음 값은 기동 시각, 끝까지 돌면 그 시각, 건너뜀 · 저장소 닿지 않음 · 예외면 그대로")
    void imageCleanup() {
        ReviewImageCleanupService service = mock(ReviewImageCleanupService.class);
        CommunityReviewImageCleanupScheduler scheduler = new CommunityReviewImageCleanupScheduler(service, metrics);
        assertThat(lastSuccess("review_image_cleanup")).isEqualTo(START.getEpochSecond());

        clock.set(LATER);
        when(service.run()).thenReturn(run(true, false));
        scheduler.cleanup();
        when(service.run()).thenReturn(run(false, true));
        scheduler.cleanup();
        assertThat(lastSuccess("review_image_cleanup")).isEqualTo(START.getEpochSecond());

        when(service.run()).thenThrow(new IllegalStateException("db down"));
        scheduler.cleanup();
        assertThat(lastSuccess("review_image_cleanup")).isEqualTo(START.getEpochSecond());

        doReturn(run(false, false)).when(service).run();
        scheduler.cleanup();
        assertThat(lastSuccess("review_image_cleanup")).isEqualTo(LATER.getEpochSecond());
        assertThat(registry.find(CommunityMetrics.SCHEDULER_LAST_SUCCESS).tag("task", "review_moderation").gauges())
            .isEmpty();
    }

    private static CleanupRun run(boolean skipped, boolean unavailable) {
        return new CleanupRun(new PendingRun(0, 0, 0, 0), new SweepRun(0, 0, 0, false, false), skipped, unavailable);
    }

    private double lastSuccess(String task) {
        return registry.get(CommunityMetrics.SCHEDULER_LAST_SUCCESS).tag("task", task).gauge().value();
    }
}
