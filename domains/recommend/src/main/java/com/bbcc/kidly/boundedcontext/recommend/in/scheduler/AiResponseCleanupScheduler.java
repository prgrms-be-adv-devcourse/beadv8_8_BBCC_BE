package com.bbcc.kidly.boundedcontext.recommend.in.scheduler;

import com.bbcc.kidly.boundedcontext.recommend.app.ai.AiResponseRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 보관 기간이 지난 AI 응답을 지운다. 테스트에서는 정리 서비스를 직접 불러 확인하므로 스케줄러를 만들지 않는다.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class AiResponseCleanupScheduler {

    private final AiResponseRetentionService retentionService;

    // 04:30은 03:00에 시작하는 선호 Job과 겹치지 않는 시각이고, 서버 여러 대가 함께 지워도 결과가 같아 잠금을 두지 않는다.
    @Scheduled(cron = "${kidly.recommend.ai.response-cleanup-cron:0 30 4 * * *}", zone = "Asia/Seoul")
    public void deleteExpiredResponses() {
        try {
            retentionService.deleteExpiredResponses();
        } catch (RuntimeException e) {
            // 예외 메시지와 스택 트레이스에는 DB 오류의 행 값이 들어갈 수 있어 예외 종류만 남긴다
            log.error("AI 응답 정리가 예외로 끝났습니다. error={}", e.getClass().getSimpleName());
        }
    }
}
