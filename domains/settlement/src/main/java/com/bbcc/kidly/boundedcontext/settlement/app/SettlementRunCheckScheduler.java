package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayLookupException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 영업일 하루 한 번, 그날 기준일의 정산 실행이 끝났는지 확인해 없으면 ERROR를 남긴다(Grafana 알림).
 * 누가 실행했는지(k3s CronJob · 매시간 스케줄러 · 관리자)는 보지 않는다. 멈춘 실행(2시간 넘게 실행 중)도 같은 확인에서 알린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementRunCheckScheduler {

    private static final Duration STUCK_AFTER = Duration.ofHours(2);

    private final SettlementRunService settlementRunService;
    private final SettlementBusinessCalendar settlementBusinessCalendar;

    @Scheduled(cron = "${kidly.settlement.run-check-cron}", zone = "Asia/Seoul")
    public void checkToday() {
        var today = LocalDate.now();
        try {
            if (!settlementBusinessCalendar.isBusinessDay(today)) {
                log.info("영업일이 아니라 정산 실행을 확인하지 않습니다: {}", today);
                return;
            }
        } catch (SettlementHolidayLookupException e) {
            // 공휴일 목록 없음은 캘린더가 ERROR를 남기며 CronJob 실행도 실패한다.
            log.warn("영업일을 판단하지 못해 정산 실행을 확인하지 않습니다: {}", e.getMessage());
            return;
        }
        if (settlementRunService.isCompleted(today)) {
            return;
        }
        settlementRunService.findRunningSince()
            .filter(running -> running.startedAt().isBefore(LocalDateTime.now().minus(STUCK_AFTER)))
            .ifPresentOrElse(
                running -> log.error("정산 실행 {}이 {}부터 끝나지 않아 오늘 정산이 끝나지 않았습니다. 관리자 복구(recover)가 필요합니다",
                    running.runId(), running.startedAt()),
                () -> log.error("오늘({}) 끝난 정산 실행이 없습니다. CronJob settlement-daily-run 실행 기록을 확인하세요", today));
    }
}
