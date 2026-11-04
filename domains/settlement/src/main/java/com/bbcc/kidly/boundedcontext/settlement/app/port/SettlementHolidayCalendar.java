package com.bbcc.kidly.boundedcontext.settlement.app.port;

import java.time.LocalDate;
import java.time.Year;
import java.util.Set;

/**
 * 한 해의 공휴일(대체공휴일 · 임시공휴일 포함)을 조회한다.
 * 조회하지 못하면(인증키 없음, 연결 · 시간 초과, 오류 응답, 해석 실패) {@link SettlementHolidayLookupException}을 던진다.
 */
public interface SettlementHolidayCalendar {

    Set<LocalDate> holidaysIn(Year year);
}
