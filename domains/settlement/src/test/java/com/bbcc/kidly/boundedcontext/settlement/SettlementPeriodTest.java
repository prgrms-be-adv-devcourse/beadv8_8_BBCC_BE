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
    @DisplayName("종료일은 기준일 전날이다")
    void from() {
        var period = SettlementPeriod.from(CUTOFF_AT);

        assertThat(period.cutoffAt()).isEqualTo(CUTOFF_AT);
        assertThat(period.endOn()).isEqualTo(LocalDate.of(2026, 10, 31));
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
    @DisplayName("자동 실행은 실행일 0시를 종료 기준으로 해 전날까지 확정된 대상을 정산한다")
    void daily() {
        var period = SettlementPeriod.daily(LocalDate.of(2026, 11, 2));

        assertThat(period.cutoffAt()).isEqualTo(LocalDateTime.of(2026, 11, 2, 0, 0));
        assertThat(period.endOn()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    @DisplayName("자동 실행일이 달 첫날이면 종료일은 지난달 말일이다")
    void dailyFirstDayOfMonth() {
        var period = SettlementPeriod.daily(LocalDate.of(2026, 12, 1));

        assertThat(period.cutoffAt()).isEqualTo(LocalDateTime.of(2026, 12, 1, 0, 0));
        assertThat(period.endOn()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    @DisplayName("자동 실행일이 없으면 만들 수 없다")
    void dailyRejectsNull() {
        assertThatThrownBy(() -> SettlementPeriod.daily(null)).isInstanceOf(NullPointerException.class);
    }
}
