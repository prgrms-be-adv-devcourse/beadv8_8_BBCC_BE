package com.bbcc.kidly.boundedcontext.settlement.app.port;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementHoliday;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Set;

/**
 * 저장된 공휴일(settlement_holiday). 여러 서버가 동시에 upsert해도 결과가 같다.
 */
public interface SettlementHolidays {

    Set<LocalDate> holidaysIn(Year year);

    void upsert(List<SettlementHoliday> holidays, LocalDateTime now);
}
