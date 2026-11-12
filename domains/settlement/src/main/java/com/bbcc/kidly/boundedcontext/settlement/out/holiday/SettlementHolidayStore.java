package com.bbcc.kidly.boundedcontext.settlement.out.holiday;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidays;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementHoliday;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 조회 후 저장(JPA)은 서버 두 대가 같은 날짜를 동시에 넣으면 PK 충돌로 한쪽이 실패해, ON CONFLICT 한 문장으로 넣는다.
 * 이름이 같으면 고치지 않아 updated_at은 실제로 바뀐 때만 움직인다.
 */
@Component
@RequiredArgsConstructor
public class SettlementHolidayStore implements SettlementHolidays {

    private final JdbcTemplate jdbc;

    @Override
    public Set<LocalDate> holidaysIn(Year year) {
        return Set.copyOf(jdbc.queryForList("""
            select holiday_on from settlement.settlement_holiday where holiday_on between ? and ?
            """, LocalDate.class, year.atDay(1), year.atMonth(12).atEndOfMonth()));
    }

    @Override
    public void upsert(List<SettlementHoliday> holidays, LocalDateTime now) {
        jdbc.batchUpdate("""
            insert into settlement.settlement_holiday (holiday_on, name, created_at, updated_at)
            values (?, ?, ?, ?)
            on conflict (holiday_on) do update set name = excluded.name, updated_at = excluded.updated_at
            where settlement.settlement_holiday.name is distinct from excluded.name
            """, holidays.stream().map(holiday -> new Object[] {holiday.date(), holiday.name(), now, now}).toList());
    }
}
