package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidays;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementHoliday;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SettlementHolidayStoreIntegrationTest {

    private static final LocalDate NEW_YEAR = LocalDate.of(2026, 1, 1);
    private static final LocalDateTime FIRST = LocalDateTime.of(2026, 1, 2, 12, 0);
    private static final LocalDateTime LATER = FIRST.plusDays(1);

    @Autowired
    private SettlementHolidays holidays;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from settlement.settlement_holiday");
    }

    @Test
    @DisplayName("같은 공휴일을 두 번 저장해도 행 · 이름 · 수정 시각이 그대로다")
    void upsertIsIdempotent() {
        var list = List.of(new SettlementHoliday(NEW_YEAR, "1월1일"));

        holidays.upsert(list, FIRST);
        holidays.upsert(list, LATER);

        assertThat(rows()).containsExactly(Map.of("holiday_on", NEW_YEAR, "name", "1월1일", "updated_at", FIRST));
    }

    @Test
    @DisplayName("이름이 바뀌면 이름과 수정 시각만 갱신한다")
    void updatesChangedName() {
        holidays.upsert(List.of(new SettlementHoliday(NEW_YEAR, "1월1일")), FIRST);

        holidays.upsert(List.of(new SettlementHoliday(NEW_YEAR, "신정")), LATER);

        assertThat(rows()).containsExactly(Map.of("holiday_on", NEW_YEAR, "name", "신정", "updated_at", LATER));
    }

    @Test
    @DisplayName("조회한 해의 공휴일만 돌려준다")
    void readsOnlyThatYear() {
        holidays.upsert(List.of(new SettlementHoliday(LocalDate.of(2025, 12, 25), "기독탄신일"),
            new SettlementHoliday(NEW_YEAR, "1월1일"), new SettlementHoliday(LocalDate.of(2027, 1, 1), "1월1일")), FIRST);

        assertThat(holidays.holidaysIn(Year.of(2026))).containsExactly(NEW_YEAR);
    }

    private List<Map<String, Object>> rows() {
        return jdbc.query("select holiday_on, name, updated_at from settlement.settlement_holiday", (rs, rowNum) ->
            Map.of("holiday_on", rs.getObject("holiday_on", LocalDate.class), "name", rs.getString("name"),
                "updated_at", rs.getObject("updated_at", LocalDateTime.class)));
    }
}
