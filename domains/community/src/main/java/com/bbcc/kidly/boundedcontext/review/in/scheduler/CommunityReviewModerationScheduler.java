package com.bbcc.kidly.boundedcontext.review.in.scheduler;

import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 검사 대기 리뷰를 주기마다 검사한다(kidly.community.moderation.enabled가 true일 때). 서버가 여러 대여도 한 대만 돈다(DB 잠금).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.community.moderation.enabled", havingValue = "true", matchIfMissing = true)
public class CommunityReviewModerationScheduler {

    private final ReviewModerationService moderationService;

    // fixedDelay는 이전 실행이 끝난 뒤부터 주기를 재므로 실행이 겹치지 않는다. 첫 실행도 한 주기 뒤로 미룬다.
    // 예외 메시지에 값이 들어갈 수 있어 예외 종류만 남긴다
    @Scheduled(fixedDelayString = "${kidly.community.moderation.interval}",
        initialDelayString = "${kidly.community.moderation.interval}")
    public void moderate() {
        try {
            moderationService.processPending();
        } catch (RuntimeException e) {
            log.error("리뷰 검사 주기가 예외로 끝났습니다. error={}", e.getClass().getSimpleName());
        }
    }
}
