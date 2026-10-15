package com.bbcc.kidly.boundedcontext.settlement;

import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.CUTOFF_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementPeriod;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementPeriodTest {

    @Test
    @DisplayName("기준일 전날이 속한 달 1일부터 기준일 전날까지가 기간이다")
    void from() {
        var period = SettlementPeriod.from(CUTOFF_AT);

        assertThat(period.cutoffAt()).isEqualTo(CUTOFF_AT);
        assertThat(period.startOn()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(period.endOn()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    @DisplayName("달 중간 기준일이면 그 달 1일부터 기준일 전날까지가 기간이다")
    void fromMidMonth() {
        var period = SettlementPeriod.from(LocalDateTime.of(2026, 10, 16, 0, 0));

        assertThat(period.startOn()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(period.endOn()).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    @DisplayName("1일 기준이면 지난달 전체가 기간이다")
    void fromFirstDay() {
        var period = SettlementPeriod.from(LocalDateTime.of(2026, 10, 1, 0, 0));

        assertThat(period.startOn()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(period.endOn()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    @DisplayName("기준일이 0시가 아니면 만들 수 없다")
    void fromRejectsNotMidnight() {
        assertThatThrownBy(() -> SettlementPeriod.from(LocalDateTime.of(2026, 11, 1, 0, 0, 1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("기준일이 없으면 만들 수 없다")
    void fromRejectsNull() {
        assertThatThrownBy(() -> SettlementPeriod.from(null)).isInstanceOf(NullPointerException.class);
    }
}
