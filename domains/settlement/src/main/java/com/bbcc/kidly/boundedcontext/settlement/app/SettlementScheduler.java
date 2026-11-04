package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayLookupException;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 영업일마다 전날까지 확정된 대상을 자동 정산한다. 영업일 낮 동안 매시간 시도하고, 그날 처음 성공한 실행만 정산한다.
 * 서버가 여러 대여도 같은 기준일은 작업 인스턴스가 한 번만 완료되게 막는다. 관리자 수동 실행은 영업일을 보지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

    private final SettlementRunService settlementRunService;
    private final SettlementBusinessCalendar settlementBusinessCalendar;
    private static final Duration STUCK_AFTER = Duration.ofHours(2);

    // 공휴일 · 조회 실패 · 이미 정산한 기준일 · 실행 중이면 로그만 남기고 넘어간다. 조회 실패는 다음 시간에 다시 시도한다
    @Scheduled(cron = "${kidly.settlement.run-cron}", zone = "Asia/Seoul")
    public void runDaily() {
        var today = LocalDate.now();
        if (!isBusinessDay(today)) {
            return;
        }
        try {
            var run = settlementRunService.runScheduled(today);
            log.info("정산 실행 {}: {} 성공 {} 건너뜀 {}", run.runId(), run.status(), run.successCount(), run.failCount());
        } catch (BusinessException e) {
            if (isExpected(e)) {
                log.info("정산 자동 실행을 건너뜁니다: {}", e.getDetail());
                alertIfStuck(e);
            } else {
                log.warn("정산 자동 실행을 건너뜁니다: {}", e.getDetail());
            }
        }
    }

    // 서버가 실행 도중 죽으면 기록이 STARTED로 남아 매시간 "실행 중"으로 건너뛴다. 정산은 몇 초면 끝나므로
    // 2시간 넘게 "실행 중"이면 멈춘 것이다. ERROR는 Grafana 알림으로 이어지고, 관리자 복구(settlement-runs/{id}/recover)가 필요하다
    // 진행 없는 기록은 다음 실행이 stale-run-after(기본 30분) 뒤 스스로 닫으므로, 이 알림은 그것도 못 닫은 경우다
    private void alertIfStuck(BusinessException e) {
        if (e.getErrorCode() != SettlementErrorCode.SETTLEMENT_RUN_IN_PROGRESS) {
            return;
        }
        try {
            settlementRunService.findRunningSince()
                .filter(running -> running.startedAt().isBefore(LocalDateTime.now().minus(STUCK_AFTER)))
                .ifPresent(running -> log.error(
                    "정산 실행 {}이 {}부터 끝나지 않아 자동 정산이 멈춰 있습니다. 관리자 복구(recover)가 필요합니다",
                    running.runId(), running.startedAt()));
        } catch (RuntimeException lookupFailure) {
            // 알림 보조 조회가 실패해도 건너뛰기 자체는 끝난 뒤라 본 흐름에 영향이 없다
            log.warn("멈춘 정산 실행을 확인하지 못했습니다. error={}", lookupFailure.getClass().getSimpleName());
        }
    }

    // 조회 예외 메시지에는 인증키가 없다(SettlementHolidayLookupException)
    private boolean isBusinessDay(LocalDate today) {
        try {
            if (settlementBusinessCalendar.isBusinessDay(today)) {
                return true;
            }
            log.info("영업일이 아니라 정산 자동 실행을 건너뜁니다: {}", today);
        } catch (SettlementHolidayLookupException e) {
            log.warn("영업일을 판단하지 못해 이번 정산 자동 실행을 건너뜁니다: {}", e.getMessage());
        }
        return false;
    }

    // 매시간 시도하므로 그날 이미 정산했거나 다른 실행이 도는 것은 정상 흐름이다
    private static boolean isExpected(BusinessException e) {
        return e.getErrorCode() == SettlementErrorCode.SETTLEMENT_ALREADY_RUN
            || e.getErrorCode() == SettlementErrorCode.SETTLEMENT_RUN_IN_PROGRESS;
    }
}
