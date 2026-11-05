package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayCalendar;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayLookupException;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementBusinessDay;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 정산 영업일 달력. 그해 공휴일을 한 번만 조회해 메모리에 두고 영업일 · 다음 영업일을 계산한다.
 * 조회에 실패하면 공휴일은 남기지 않고 실패 시각만 기억해 5분 동안은 API를 부르지 않는다(외부 장애 때 요청마다 시간 제한까지 기다리지 않게).
 * 임시공휴일 지정을 반영하려고 하루 1회 다시 받는다(SettlementHolidayCacheScheduler). 다시 받지 못하면 전 값을 유지한다.
 */
@Service
@RequiredArgsConstructor
public class SettlementBusinessCalendar {

    private final SettlementHolidayCalendar holidayCalendar;
    private static final Duration RETRY_AFTER = Duration.ofMinutes(5);

    private final Map<Year, Set<LocalDate>> holidaysByYear = new ConcurrentHashMap<>();
    private volatile Instant failedAt;

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

    /**
     * 메모리에 둔 해마다 공휴일을 다시 받아 성공한 해만 바꾼다. 조회에 실패한 해는 전에 받은 공휴일을 그대로 둔다.
     * 그래서 공휴일 API가 며칠 죽어 있어도 정산은 어제 공휴일로 계속 돈다(#808). 비어 있으면 아무것도 하지 않는다(첫 조회가 받는다).
     *
     * @return 다시 받지 못한 해의 수
     */
    public int refresh() {
        failedAt = null;
        int failed = 0;
        for (Year year : holidaysByYear.keySet()) {
            try {
                holidaysByYear.put(year, holidayCalendar.holidaysIn(year));
            } catch (SettlementHolidayLookupException e) {
                failed++;
            }
        }
        return failed;
    }

    private Set<LocalDate> holidaysOf(Year year) {
        var cached = holidaysByYear.get(year);
        if (cached != null) {
            return cached;
        }
        var lastFailure = failedAt;
        if (lastFailure != null && Instant.now().isBefore(lastFailure.plus(RETRY_AFTER))) {
            throw new SettlementHolidayLookupException("공휴일 조회가 최근에 실패해 잠시 다시 시도하지 않습니다");
        }
        try {
            return holidaysByYear.computeIfAbsent(year, holidayCalendar::holidaysIn);
        } catch (SettlementHolidayLookupException e) {
            failedAt = Instant.now();
            throw e;
        }
    }
}
