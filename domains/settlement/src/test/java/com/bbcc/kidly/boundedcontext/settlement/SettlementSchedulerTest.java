package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementBusinessCalendar;
import com.bbcc.kidly.boundedcontext.settlement.app.SettlementRunService;
import com.bbcc.kidly.boundedcontext.settlement.app.SettlementScheduler;
import com.bbcc.kidly.boundedcontext.settlement.app.dto.SettlementRunResult;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayLookupException;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

// 스케줄러는 LocalDate.now()로 오늘을 정하므로 오늘 날짜로 확인한다
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class SettlementSchedulerTest {

    @Mock
    private SettlementRunService settlementRunService;

    @Mock
    private SettlementBusinessCalendar settlementBusinessCalendar;

    @InjectMocks
    private SettlementScheduler scheduler;

    @Test
    @DisplayName("영업일이면 오늘 기준으로 정산을 실행한다")
    void runsOnBusinessDay() {
        var today = LocalDate.now();
        given(settlementBusinessCalendar.isBusinessDay(today)).willReturn(true);
        given(settlementRunService.runScheduled(today)).willReturn(new SettlementRunResult(1L, today.atStartOfDay(),
            today.minusDays(1), today.minusDays(1), "SCHEDULED", "COMPLETED", 0, 0, null, null, null, List.of()));

        scheduler.runDaily();

        then(settlementRunService).should().runScheduled(today);
    }

    @Test
    @DisplayName("공휴일이면 실행하지 않고 INFO를 남긴다")
    void skipsHoliday(CapturedOutput output) {
        given(settlementBusinessCalendar.isBusinessDay(LocalDate.now())).willReturn(false);

        scheduler.runDaily();

        then(settlementRunService).should(never()).runScheduled(any());
        assertThat(output.getOut()).contains("INFO").contains("영업일이 아니라 정산 자동 실행을 건너뜁니다");
    }

    @Test
    @DisplayName("공휴일 조회가 실패하면 실행하지 않고 실패 종류만 WARN으로 남긴다")
    void skipsWhenLookupFails(CapturedOutput output) {
        given(settlementBusinessCalendar.isBusinessDay(LocalDate.now()))
            .willThrow(new SettlementHolidayLookupException("공휴일 조회 결과가 정상이 아닙니다: resultCode 30"));

        assertThatCode(scheduler::runDaily).doesNotThrowAnyException();

        then(settlementRunService).should(never()).runScheduled(any());
        assertThat(output.getOut()).contains("WARN")
            .contains("영업일을 판단하지 못해 이번 정산 자동 실행을 건너뜁니다: 공휴일 조회 결과가 정상이 아닙니다: resultCode 30")
            .doesNotContain("SettlementHolidayLookupException");
    }

    @Test
    @DisplayName("이미 정산한 기준일이면 정상 흐름이라 INFO를 남긴다")
    void alreadyRunIsInfo(CapturedOutput output) {
        givenRunFails(SettlementErrorCode.SETTLEMENT_ALREADY_RUN);

        scheduler.runDaily();

        assertThat(skipLogLine(output)).contains("INFO");
    }

    @Test
    @DisplayName("다른 실행이 도는 중이면 정상 흐름이라 INFO를 남긴다")
    void inProgressIsInfo(CapturedOutput output) {
        givenRunFails(SettlementErrorCode.SETTLEMENT_RUN_IN_PROGRESS);

        scheduler.runDaily();

        assertThat(skipLogLine(output)).contains("INFO");
    }

    @Test
    @DisplayName("그 밖의 업무 예외는 WARN을 남긴다")
    void otherBusinessErrorIsWarn(CapturedOutput output) {
        givenRunFails(SettlementErrorCode.INVALID_SETTLEMENT_CUTOFF);

        scheduler.runDaily();

        assertThat(skipLogLine(output)).contains("WARN");
    }

    private void givenRunFails(SettlementErrorCode errorCode) {
        var today = LocalDate.now();
        given(settlementBusinessCalendar.isBusinessDay(today)).willReturn(true);
        given(settlementRunService.runScheduled(today)).willThrow(new BusinessException(errorCode));
    }

    private static String skipLogLine(CapturedOutput output) {
        return output.getOut().lines().filter(line -> line.contains("정산 자동 실행을 건너뜁니다"))
            .findFirst().orElseThrow();
    }
}
