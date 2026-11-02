package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayCalendar;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayLookupException;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementBusinessDay;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 정산 영업일 달력. 그날이 속한 달의 공휴일을 조회해 영업일인지 판단한다. 캐시는 두지 않는다(실행당 1회 조회).
 */
@Service
@RequiredArgsConstructor
public class SettlementBusinessCalendar {

    private final SettlementHolidayCalendar holidayCalendar;

    /**
     * @throws SettlementHolidayLookupException 공휴일을 조회하지 못한 경우. 영업일인지 알 수 없다
     */
    public boolean isBusinessDay(LocalDate date) {
        return SettlementBusinessDay.isBusinessDay(date, holidayCalendar.holidaysIn(YearMonth.from(date)));
    }
}
