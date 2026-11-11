package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementBusinessCalendar;
import com.bbcc.kidly.boundedcontext.settlement.app.SettlementDailyRunner;
import com.bbcc.kidly.boundedcontext.settlement.app.SettlementRunService;
import com.bbcc.kidly.boundedcontext.settlement.app.dto.SettlementRunResult;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayLookupException;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementErrorCode;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRunStatus;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementDailyRunnerTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Mock
    private SettlementRunService service;

    @Mock
    private SettlementBusinessCalendar calendar;

    @InjectMocks
    private SettlementDailyRunner runner;

    @Test
    @DisplayName("비영업일에는 정산하지 않고 0으로 끝난다")
    void skipsNonBusinessDay() {
        runner.run(null);

        assertThat(runner.getExitCode()).isZero();
        then(service).should(never()).runScheduled(any());
    }

    @Test
    @DisplayName("정산을 마치면(일부 판매자를 건너뛰어도) 0으로 끝난다")
    void completed() {
        assertThat(exitCodeOf(SettlementRunStatus.COMPLETED)).isZero();
        assertThat(exitCodeOf(SettlementRunStatus.PARTIAL_FAILED)).isZero();
    }

    @Test
    @DisplayName("정산 작업이 실패하면 1로 끝난다")
    void failed() {
        assertThat(exitCodeOf(SettlementRunStatus.FAILED)).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 정산한 기준일이면 0으로 끝난다")
    void alreadyRun() {
        given(calendar.isBusinessDay(TODAY)).willReturn(true);
        given(service.runScheduled(TODAY)).willThrow(new BusinessException(SettlementErrorCode.SETTLEMENT_ALREADY_RUN));

        runner.run(null);

        assertThat(runner.getExitCode()).isZero();
    }

    @Test
    @DisplayName("다른 실행이 돌고 있으면 예외로 끝난다(기동 실패라 종료 코드가 0이 아니다)")
    void inProgress() {
        given(calendar.isBusinessDay(TODAY)).willReturn(true);
        given(service.runScheduled(TODAY))
            .willThrow(new BusinessException(SettlementErrorCode.SETTLEMENT_RUN_IN_PROGRESS));

        assertThatThrownBy(() -> runner.run(null)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("영업일을 판단하지 못하면 정산하지 않고 예외로 끝난다")
    void holidayLookupFails() {
        given(calendar.isBusinessDay(TODAY)).willThrow(new SettlementHolidayLookupException("조회 실패"));

        assertThatThrownBy(() -> runner.run(null)).isInstanceOf(SettlementHolidayLookupException.class);
        then(service).should(never()).runScheduled(any());
    }

    private int exitCodeOf(SettlementRunStatus status) {
        given(calendar.isBusinessDay(TODAY)).willReturn(true);
        given(service.runScheduled(TODAY)).willReturn(new SettlementRunResult(1L, TODAY.atStartOfDay(),
            TODAY.minusDays(1), TODAY.minusDays(1), "SCHEDULED", status.name(), 0, 0, null, null, null, List.of()));
        runner.run(null);
        return runner.getExitCode();
    }
}
