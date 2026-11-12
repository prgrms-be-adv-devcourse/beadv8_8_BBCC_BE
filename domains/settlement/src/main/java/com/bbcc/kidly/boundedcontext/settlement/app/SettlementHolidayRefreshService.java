package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayCalendar;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayLookupException;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidays;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 공휴일 API에서 올해 · 내년 공휴일을 받아 저장한다. 받지 못한 해는 저장된 행을 그대로 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementHolidayRefreshService {

    private final SettlementHolidayCalendar holidayCalendar;
    private final SettlementHolidays holidays;

    public void refresh() {
        var thisYear = Year.now();
        for (var year : List.of(thisYear, thisYear.plusYears(1))) {
            try {
                holidays.upsert(holidayCalendar.holidaysIn(year), LocalDateTime.now());
            } catch (SettlementHolidayLookupException e) {
                log.warn("{}년 공휴일을 갱신하지 못해 저장된 공휴일을 그대로 씁니다: {}", year, e.getMessage());
            }
        }
    }
}
