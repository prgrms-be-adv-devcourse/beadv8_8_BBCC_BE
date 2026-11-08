package com.bbcc.kidly.boundedcontext.review.in.scheduler;

import com.bbcc.kidly.boundedcontext.review.app.ReviewImageCleanupService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.community.review.image.cleanup.enabled", havingValue = "true",
    matchIfMissing = false)
public class CommunityReviewImageCleanupScheduler {

    private final ReviewImageCleanupService cleanupService;

    // fixedDelay는 이전 실행이 끝난 뒤부터 주기를 재므로 실행이 겹치지 않는다. 첫 실행도 한 주기 뒤로 미룬다.
    // 예외 메시지에 key가 들어갈 수 있어 예외 종류만 남긴다
    @Scheduled(fixedDelayString = "${kidly.community.review.image.cleanup.interval}",
        initialDelayString = "${kidly.community.review.image.cleanup.interval}")
    public void cleanup() {
        try {
            cleanupService.run();
        } catch (RuntimeException e) {
            log.error("리뷰 사진 정리 주기가 예외로 끝났습니다. error={}", e.getClass().getSimpleName());
        }
    }
}
