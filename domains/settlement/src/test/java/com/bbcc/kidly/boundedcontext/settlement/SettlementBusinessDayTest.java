package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementBusinessDay;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SettlementBusinessDayTest {

    // 2026-01-01(목) 신정, 2026-03-02(월) 대체공휴일(삼일절)
    private static final Set<LocalDate> HOLIDAYS = Set.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 2));

    @Test
    @DisplayName("공휴일이 아닌 평일은 영업일이다")
    void weekday() {
        assertThat(SettlementBusinessDay.isBusinessDay(LocalDate.of(2026, 11, 2), HOLIDAYS)).isTrue();
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"2026-10-31", "2026-11-01"})
    @DisplayName("토 · 일요일은 영업일이 아니다")
    void weekend(String date) {
        assertThat(SettlementBusinessDay.isBusinessDay(LocalDate.parse(date), Set.of())).isFalse();
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"2026-01-01", "2026-03-02"})
    @DisplayName("공휴일 · 대체공휴일은 평일이어도 영업일이 아니다")
    void holiday(String date) {
        assertThat(SettlementBusinessDay.isBusinessDay(LocalDate.parse(date), HOLIDAYS)).isFalse();
    }
}
