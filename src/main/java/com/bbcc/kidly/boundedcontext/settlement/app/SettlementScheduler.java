package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매월 지난달 확정분을 자동 정산한다. 서버가 여러 대여도 같은 기준일은 작업 인스턴스가 한 번만 완료되게 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

    private final SettlementRunService settlementRunService;

    // 이미 정산한 기준일 · 실행 중이면 로그만 남기고 넘어간다
    @Scheduled(cron = "${kidly.settlement.run-cron}", zone = "Asia/Seoul")
    public void runMonthly() {
        try {
            var run = settlementRunService.runScheduled(LocalDate.now());
            log.info("정산 실행 {}: {} 성공 {} 건너뜀 {}", run.runId(), run.status(), run.successCount(), run.failCount());
        } catch (BusinessException e) {
            log.warn("정산 자동 실행을 건너뜁니다: {}", e.getDetail());
        }
    }
}
