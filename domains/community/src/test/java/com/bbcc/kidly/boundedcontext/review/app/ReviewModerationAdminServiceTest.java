package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImages;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewModerationStats;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewModerationStats.StatusCountRow;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewModerations;
import com.bbcc.kidly.boundedcontext.review.app.port.Reviews;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationStatus;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Limit;

/**
 * 검사 현황(overview)의 대기 수: PENDING = 전체 대기 − 재시도 상한에 닿은 대기. 두 수는 따로 세므로 그 사이에 상태가 바뀌면 상한 도달 수가
 * 전체 대기 수보다 클 수 있는데, 그때도 PENDING은 음수가 아니라 0이다.
 */
class ReviewModerationAdminServiceTest {

    private static final int MAX_ATTEMPTS = 3;

    private final ReviewModerationStats stats = mock(ReviewModerationStats.class);
    private final ReviewModerationAdminService service = new ReviewModerationAdminService(
        mock(ReviewModerations.class), mock(Reviews.class), mock(ReviewImages.class), mock(ReviewStatsShare.class),
        new ReviewModerationProperties(false, Duration.ofMinutes(1), 10, MAX_ATTEMPTS), stats,
        mock(com.bbcc.kidly.boundedcontext.review.app.port.ReviewActivityLog.class));

    @ParameterizedTest(name = "전체 대기 {0} · 상한 도달 {1} → PENDING {2}")
    @CsvSource({"7, 2, 5", "2, 2, 0", "2, 5, 0", "0, 1, 0"})
    @DisplayName("PENDING은 전체 대기에서 상한 도달을 뺀 값이고, 상한 도달이 더 크면(동시 갱신) 0이다. 상한 도달 수는 그대로 보인다")
    void pendingNeverNegative(long pendingTotal, long exhausted, long expectedPending) {
        StatusCountRow pending = mock(StatusCountRow.class);
        when(pending.getStatus()).thenReturn(ModerationStatus.PENDING);
        when(pending.getTotal()).thenReturn(pendingTotal);
        when(stats.countByModerationStatus()).thenReturn(List.of(pending));
        when(stats.countExhaustedPending(MAX_ATTEMPTS)).thenReturn(exhausted);
        when(stats.findRecentlyHidden(any(Limit.class))).thenReturn(List.of());

        Map<String, Long> counts = service.overview(10).counts();

        assertThat(counts).containsEntry("PENDING", expectedPending).containsEntry("PENDING_EXHAUSTED", exhausted)
            .containsEntry("PASSED", 0L).containsEntry("HIDDEN", 0L);
    }
}
