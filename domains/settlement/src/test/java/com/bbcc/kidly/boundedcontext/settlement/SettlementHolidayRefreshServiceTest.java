package com.bbcc.kidly.boundedcontext.settlement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementHolidayRefreshService;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayCalendar;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayLookupException;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidays;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementHoliday;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementHolidayRefreshServiceTest {

    private static final Year THIS_YEAR = Year.now();
    private static final Year NEXT_YEAR = THIS_YEAR.plusYears(1);

    @Mock
    private SettlementHolidayCalendar holidayCalendar;

    @Mock
    private SettlementHolidays holidays;

    @InjectMocks
    private SettlementHolidayRefreshService service;

    @Test
    @DisplayName("올해와 내년 공휴일을 받아 저장한다")
    void savesThisAndNextYear() {
        var thisYear = List.of(new SettlementHoliday(THIS_YEAR.atDay(1), "1월1일"));
        var nextYear = List.of(new SettlementHoliday(NEXT_YEAR.atDay(1), "1월1일"));
        given(holidayCalendar.holidaysIn(THIS_YEAR)).willReturn(thisYear);
        given(holidayCalendar.holidaysIn(NEXT_YEAR)).willReturn(nextYear);

        service.refresh();

        verify(holidays).upsert(eq(thisYear), any(LocalDateTime.class));
        verify(holidays).upsert(eq(nextYear), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("받지 못한 해는 저장하지 않고(저장된 행 유지) 다른 해는 저장한다")
    void keepsStoredRowsOfFailedYear() {
        var nextYear = List.of(new SettlementHoliday(NEXT_YEAR.atDay(1), "1월1일"));
        given(holidayCalendar.holidaysIn(THIS_YEAR)).willThrow(new SettlementHolidayLookupException("공휴일 조회에 실패했습니다"));
        given(holidayCalendar.holidaysIn(NEXT_YEAR)).willReturn(nextYear);

        service.refresh();

        verify(holidays).upsert(eq(nextYear), any(LocalDateTime.class));
        verifyNoMoreInteractions(holidays);
    }
}
