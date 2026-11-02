package com.bbcc.kidly.boundedcontext.settlement.domain;

import static java.util.Objects.requireNonNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * 정산 영업일 규칙. 토 · 일요일과 공휴일(대체공휴일 포함)이 아닌 날이 영업일이다.
 */
public final class SettlementBusinessDay {

    private SettlementBusinessDay() {
    }

    public static boolean isBusinessDay(LocalDate date, Set<LocalDate> holidays) {
        requireNonNull(date, "date는 필수입니다");
        requireNonNull(holidays, "holidays는 필수입니다");
        var dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY && !holidays.contains(date);
    }
}
