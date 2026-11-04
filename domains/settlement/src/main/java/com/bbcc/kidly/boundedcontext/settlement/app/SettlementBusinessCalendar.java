package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayCalendar;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayLookupException;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementBusinessDay;
import java.time.LocalDate;
import java.time.Year;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 정산 영업일 달력. 그해 공휴일을 한 번만 조회해 메모리에 두고 영업일 · 다음 영업일을 계산한다.
 * 조회에 실패하면 아무것도 남기지 않아 다음 호출 때 다시 조회한다. 임시공휴일 지정을 반영하려고 하루 1회 비운다(SettlementHolidayCacheScheduler).
 */
@Service
@RequiredArgsConstructor
public class SettlementBusinessCalendar {

    private final SettlementHolidayCalendar holidayCalendar;
    private final Map<Year, Set<LocalDate>> holidaysByYear = new ConcurrentHashMap<>();

    /**
     * @throws SettlementHolidayLookupException 공휴일을 조회하지 못한 경우. 영업일인지 알 수 없다
     */
    public boolean isBusinessDay(LocalDate date) {
        return SettlementBusinessDay.isBusinessDay(date, holidaysOf(Year.from(date)));
    }

    /**
     * 그날 다음의 첫 영업일. 확정일이 금요일이면 월요일, 연말이면 다음 해 첫 영업일이다.
     *
     * @throws SettlementHolidayLookupException 공휴일을 조회하지 못한 경우
     */
    public LocalDate nextBusinessDayAfter(LocalDate date) {
        var next = date.plusDays(1);
        while (!isBusinessDay(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    public void clearCache() {
        holidaysByYear.clear();
    }

    private Set<LocalDate> holidaysOf(Year year) {
        return holidaysByYear.computeIfAbsent(year, holidayCalendar::holidaysIn);
    }
}
