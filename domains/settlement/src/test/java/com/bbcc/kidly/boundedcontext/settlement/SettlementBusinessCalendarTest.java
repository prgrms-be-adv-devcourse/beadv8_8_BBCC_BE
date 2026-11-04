package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementBusinessCalendar;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayCalendar;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayLookupException;
import java.time.LocalDate;
import java.time.Year;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementBusinessCalendarTest {

    private static final Year Y2026 = Year.of(2026);
    private static final Year Y2027 = Year.of(2027);

    @Mock
    private SettlementHolidayCalendar holidayCalendar;

    @InjectMocks
    private SettlementBusinessCalendar calendar;

    @Test
    @DisplayName("그해 공휴일을 조회해 평일이면 영업일, 공휴일이면 영업일이 아니라고 판단한다")
    void judgesWithHolidaysOfYear() {
        given(holidayCalendar.holidaysIn(Y2026)).willReturn(Set.of(LocalDate.of(2026, 3, 2)));

        assertThat(calendar.isBusinessDay(LocalDate.of(2026, 3, 3))).isTrue();
        assertThat(calendar.isBusinessDay(LocalDate.of(2026, 3, 2))).isFalse();
    }

    @Test
    @DisplayName("같은 해는 한 번만 조회하고, 비우면 다시 조회한다")
    void cachesPerYear() {
        given(holidayCalendar.holidaysIn(Y2026)).willReturn(Set.of());

        calendar.isBusinessDay(LocalDate.of(2026, 3, 3));
        calendar.isBusinessDay(LocalDate.of(2026, 10, 5));
        verify(holidayCalendar, times(1)).holidaysIn(Y2026);

        calendar.clearCache();
        calendar.isBusinessDay(LocalDate.of(2026, 3, 3));
        verify(holidayCalendar, times(2)).holidaysIn(Y2026);
    }

    @Test
    @DisplayName("공휴일을 조회하지 못하면 예외를 올리고, 5분 동안은 API를 다시 부르지 않는다. 비우면 다시 조회한다")
    void propagatesLookupFailureAndBacksOff() {
        given(holidayCalendar.holidaysIn(Y2026))
            .willThrow(new SettlementHolidayLookupException("공휴일 API 인증키가 없습니다"))
            .willReturn(Set.of());

        assertThatThrownBy(() -> calendar.isBusinessDay(LocalDate.of(2026, 3, 3)))
            .isInstanceOf(SettlementHolidayLookupException.class);
        assertThatThrownBy(() -> calendar.isBusinessDay(LocalDate.of(2026, 3, 3)))
            .isInstanceOf(SettlementHolidayLookupException.class);
        verify(holidayCalendar, times(1)).holidaysIn(Y2026);

        calendar.clearCache();
        assertThat(calendar.isBusinessDay(LocalDate.of(2026, 3, 3))).isTrue();
        verify(holidayCalendar, times(2)).holidaysIn(Y2026);
    }

    @Test
    @DisplayName("다음 영업일: 금요일 확정은 월요일, 공휴일 전날 확정은 공휴일 다음 평일이다")
    void nextBusinessDaySkipsWeekendAndHoliday() {
        // 2026-03-02(월)는 대체공휴일
        given(holidayCalendar.holidaysIn(Y2026)).willReturn(Set.of(LocalDate.of(2026, 3, 2)));

        assertThat(calendar.nextBusinessDayAfter(LocalDate.of(2026, 3, 5))).isEqualTo(LocalDate.of(2026, 3, 6));
        assertThat(calendar.nextBusinessDayAfter(LocalDate.of(2026, 3, 6))).isEqualTo(LocalDate.of(2026, 3, 9));
        assertThat(calendar.nextBusinessDayAfter(LocalDate.of(2026, 2, 27))).isEqualTo(LocalDate.of(2026, 3, 3));
    }

    @Test
    @DisplayName("다음 영업일이 다음 해면 그해 공휴일을 조회한다")
    void nextBusinessDayCrossesYear() {
        given(holidayCalendar.holidaysIn(Y2027)).willReturn(Set.of(LocalDate.of(2027, 1, 1)));

        // 2026-12-31(목) 확정 → 1/1(금, 신정) · 1/2(토) · 1/3(일) 건너 1/4(월)
        assertThat(calendar.nextBusinessDayAfter(LocalDate.of(2026, 12, 31))).isEqualTo(LocalDate.of(2027, 1, 4));
    }
}
