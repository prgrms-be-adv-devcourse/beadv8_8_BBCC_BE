package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementBusinessCalendar;
import com.bbcc.kidly.boundedcontext.settlement.app.SettlementRunCheckScheduler;
import com.bbcc.kidly.boundedcontext.settlement.app.SettlementRunService;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayLookupException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class SettlementRunCheckSchedulerTest {

    @Mock
    private SettlementRunService settlementRunService;

    @Mock
    private SettlementBusinessCalendar settlementBusinessCalendar;

    @InjectMocks
    private SettlementRunCheckScheduler scheduler;

    @Test
    @DisplayName("영업일이 아니면 실행 기록을 확인하지 않는다")
    void skipsNonBusinessDay(CapturedOutput output) {
        given(settlementBusinessCalendar.isBusinessDay(LocalDate.now())).willReturn(false);

        scheduler.checkToday();

        then(settlementRunService).shouldHaveNoInteractions();
        assertThat(output.toString()).contains("INFO", "영업일이 아니라 정산 실행을 확인하지 않습니다");
    }

    @Test
    @DisplayName("공휴일 목록이 없으면 경고하고 실행 기록을 확인하지 않는다")
    void skipsWhenHolidaysMissing(CapturedOutput output) {
        given(settlementBusinessCalendar.isBusinessDay(LocalDate.now()))
            .willThrow(new SettlementHolidayLookupException("공휴일 목록이 없습니다"));

        scheduler.checkToday();

        then(settlementRunService).shouldHaveNoInteractions();
        assertThat(output.toString()).contains("WARN", "영업일을 판단하지 못해 정산 실행을 확인하지 않습니다");
    }

    @Test
    @DisplayName("끝난 실행이 있으면 오류를 남기지 않는다")
    void completedRun(CapturedOutput output) {
        businessDay();
        given(settlementRunService.isCompleted(LocalDate.now())).willReturn(true);

        scheduler.checkToday();

        then(settlementRunService).should(never()).findRunningSince();
        assertThat(output.toString()).doesNotContain("ERROR");
    }

    @Test
    @DisplayName("끝난 실행이 없으면 오류를 남긴다")
    void missingRun(CapturedOutput output) {
        businessDay();
        given(settlementRunService.findRunningSince()).willReturn(Optional.empty());

        scheduler.checkToday();

        assertThat(output.toString()).contains("ERROR", "끝난 정산 실행이 없습니다");
    }

    @Test
    @DisplayName("2시간 넘게 실행 중이면 관리자 복구 오류를 남긴다")
    void stuckRun(CapturedOutput output) {
        businessDay();
        given(settlementRunService.findRunningSince()).willReturn(Optional.of(
            new SettlementRunService.RunningExecution(7L, LocalDateTime.now().minusHours(3))));

        scheduler.checkToday();

        assertThat(output.toString()).contains("ERROR", "정산 실행 7이", "관리자 복구");
    }

    @Test
    @DisplayName("2시간 안 된 실행 중이면 끝난 실행이 없다는 오류를 남긴다")
    void recentRun(CapturedOutput output) {
        businessDay();
        given(settlementRunService.findRunningSince()).willReturn(Optional.of(
            new SettlementRunService.RunningExecution(7L, LocalDateTime.now().minusMinutes(10))));

        scheduler.checkToday();

        assertThat(output.toString()).contains("ERROR", "끝난 정산 실행이 없습니다").doesNotContain("관리자 복구");
    }

    private void businessDay() {
        given(settlementBusinessCalendar.isBusinessDay(LocalDate.now())).willReturn(true);
    }
}
