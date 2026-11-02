package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementBusinessCalendar;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayCalendar;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayLookupException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementBusinessCalendarTest {

    private static final YearMonth MARCH = YearMonth.of(2026, 3);

    @Mock
    private SettlementHolidayCalendar holidayCalendar;

    @InjectMocks
    private SettlementBusinessCalendar calendar;

    @Test
    @DisplayName("그달 공휴일을 조회해 평일이면 영업일, 공휴일이면 영업일이 아니라고 판단한다")
    void judgesWithHolidaysOfMonth() {
        given(holidayCalendar.holidaysIn(MARCH)).willReturn(Set.of(LocalDate.of(2026, 3, 2)));

        assertThat(calendar.isBusinessDay(LocalDate.of(2026, 3, 3))).isTrue();
        assertThat(calendar.isBusinessDay(LocalDate.of(2026, 3, 2))).isFalse();
    }

    @Test
    @DisplayName("공휴일을 조회하지 못하면 예외를 그대로 올린다")
    void propagatesLookupFailure() {
        given(holidayCalendar.holidaysIn(MARCH)).willThrow(new SettlementHolidayLookupException("공휴일 API 인증키가 없습니다"));

        assertThatThrownBy(() -> calendar.isBusinessDay(LocalDate.of(2026, 3, 3)))
            .isInstanceOf(SettlementHolidayLookupException.class);
    }
}
