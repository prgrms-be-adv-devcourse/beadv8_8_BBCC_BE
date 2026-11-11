package com.bbcc.kidly.boundedcontext.review.in.scheduler;

import com.bbcc.kidly.boundedcontext.review.app.CommunityMetrics;
import com.bbcc.kidly.boundedcontext.review.app.CommunityMetrics.SchedulerTask;
import com.bbcc.kidly.boundedcontext.review.app.ReviewImageCleanupService;
import com.bbcc.kidly.boundedcontext.review.app.ReviewImageCleanupService.CleanupRun;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 쓰이지 않는 리뷰 사진을 주기마다 정리한다(kidly.community.review.image.cleanup.enabled가 true일 때만, 기본 꺼짐).
 * 서버가 여러 대여도 한 대만 돈다(DB 잠금). 저장소에 닿지 않는 설정이면 서비스가 바로 돌아온다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "kidly.community.review.image.cleanup.enabled", havingValue = "true",
    matchIfMissing = false)
public class CommunityReviewImageCleanupScheduler {

    private final ReviewImageCleanupService cleanupService;
    private final CommunityMetrics metrics;

    // 켜진 스케줄러만 마지막 성공 시각 지표를 갖는다(처음 값은 기동 시각)
    public CommunityReviewImageCleanupScheduler(ReviewImageCleanupService cleanupService, CommunityMetrics metrics) {
        this.cleanupService = cleanupService;
        this.metrics = metrics;
        metrics.registerScheduler(SchedulerTask.REVIEW_IMAGE_CLEANUP);
    }

    // fixedDelay는 이전 실행이 끝난 뒤부터 주기를 재므로 실행이 겹치지 않는다. 첫 실행도 한 주기 뒤로 미룬다.
    // 예외 메시지에 key가 들어갈 수 있어 예외 종류만 남긴다
    @Scheduled(fixedDelayString = "${kidly.community.review.image.cleanup.interval}",
        initialDelayString = "${kidly.community.review.image.cleanup.interval}")
    public void cleanup() {
        try {
            // 잠금을 못 잡아 건너뛰었거나 저장소에 닿지 않는 설정이면 성공으로 치지 않는다
            CleanupRun run = cleanupService.run();
            if (!run.skipped() && !run.unavailable()) {
                metrics.recordSchedulerSuccess(SchedulerTask.REVIEW_IMAGE_CLEANUP);
            }
        } catch (RuntimeException e) {
            log.error("리뷰 사진 정리 주기가 예외로 끝났습니다. error={}", e.getClass().getSimpleName());
        }
    }
}
