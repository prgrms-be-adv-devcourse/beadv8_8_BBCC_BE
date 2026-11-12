package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayLookupException;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidays;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementBusinessDay;
import java.time.LocalDate;
import java.time.Year;
import java.util.HashMap;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 저장된 공휴일(settlement_holiday)로 정산 영업일 · 다음 영업일을 계산한다. 공휴일 API는 부르지 않는다.
 * 그해 공휴일이 하나도 저장돼 있지 않으면 영업일을 판단하지 않고 {@link SettlementHolidayLookupException}을 던진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementBusinessCalendar {

    private final SettlementHolidays holidays;

    public boolean isBusinessDay(LocalDate date) {
        return SettlementBusinessDay.isBusinessDay(date, holidaysOf(Year.from(date)));
    }

    public LocalDate nextBusinessDayAfter(LocalDate date) {
        var byYear = new HashMap<Year, Set<LocalDate>>();
        var next = date.plusDays(1);
        while (!SettlementBusinessDay.isBusinessDay(next, byYear.computeIfAbsent(Year.from(next), this::holidaysOf))) {
            next = next.plusDays(1);
        }
        return next;
    }

    private Set<LocalDate> holidaysOf(Year year) {
        var result = holidays.holidaysIn(year);
        if (result.isEmpty()) {
            log.error("{}년 공휴일이 저장돼 있지 않아 영업일을 판단하지 못합니다", year);
            throw new SettlementHolidayLookupException(year + "년 공휴일이 저장돼 있지 않습니다");
        }
        return result;
    }
}
