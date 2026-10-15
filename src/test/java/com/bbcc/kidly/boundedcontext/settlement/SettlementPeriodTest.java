package com.bbcc.kidly.boundedcontext.settlement;

import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.CUTOFF_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementErrorCode;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementPeriod;
import com.bbcc.kidly.global.error.BusinessException;
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

    @Test
    @DisplayName("수동 실행은 기준일 0시를 종료 기준으로 한다. 오늘도 된다")
    void manual() {
        var today = LocalDate.of(2026, 10, 15);

        assertThat(SettlementPeriod.manual(today, today).cutoffAt()).isEqualTo(LocalDateTime.of(2026, 10, 15, 0, 0));
        assertThat(SettlementPeriod.manual(today.minusDays(5), today).cutoffAt())
            .isEqualTo(LocalDateTime.of(2026, 10, 10, 0, 0));
    }

    @Test
    @DisplayName("수동 실행 기준일이 미래면 INVALID_SETTLEMENT_CUTOFF다")
    void manualRejectsFuture() {
        var today = LocalDate.of(2026, 10, 15);

        assertThatThrownBy(() -> SettlementPeriod.manual(today.plusDays(1), today))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(SettlementErrorCode.INVALID_SETTLEMENT_CUTOFF));
    }

    @Test
    @DisplayName("수동 실행 기준일 · 오늘이 없으면 만들 수 없다")
    void manualRejectsNull() {
        var today = LocalDate.of(2026, 10, 15);

        assertThatThrownBy(() -> SettlementPeriod.manual(null, today)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SettlementPeriod.manual(today, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("자동 실행은 그 달 1일 0시를 종료 기준으로 해 지난달 전체를 정산한다")
    void monthly() {
        var period = SettlementPeriod.monthly(LocalDate.of(2026, 11, 15));

        assertThat(period.cutoffAt()).isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0));
        assertThat(period.startOn()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(period.endOn()).isEqualTo(LocalDate.of(2026, 10, 31));
    }
}
