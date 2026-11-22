package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementErrorCode;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRunStatus;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * k3s CronJob이 하루 한 번 띄우는 일회 실행이라 결과를 종료 코드로 알린다.
 * 같은 기준일을 다시 실행하면(Job 재시도나 수동 실행) SETTLEMENT_ALREADY_RUN이므로 성공으로 끝낸다. 그 밖의 예외는 0이 아닌 종료 코드가 된다.
 */
@Slf4j
@Component
@Profile(SettlementDailyRunner.PROFILE)
@RequiredArgsConstructor
public class SettlementDailyRunner implements ApplicationRunner, ExitCodeGenerator {

    public static final String PROFILE = "settlement-run-once";

    private final SettlementRunService settlementRunService;
    private final SettlementBusinessCalendar settlementBusinessCalendar;
    private int exitCode;

    @Override
    public void run(ApplicationArguments args) {
        var today = LocalDate.now();
        if (!settlementBusinessCalendar.isBusinessDay(today)) {
            log.info("영업일이 아니라 정산하지 않습니다: {}", today);
            return;
        }
        try {
            var run = settlementRunService.runScheduled(today);
            log.info("정산 실행 {}: {} 성공 {} 건너뜀 {}", run.runId(), run.status(), run.successCount(), run.failCount());
            exitCode = SettlementRunStatus.FAILED.name().equals(run.status()) ? 1 : 0;
        } catch (BusinessException e) {
            if (e.getErrorCode() != SettlementErrorCode.SETTLEMENT_ALREADY_RUN) {
                throw e;
            }
            log.info("이미 정산한 기준일입니다: {}", today);
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
