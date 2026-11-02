package com.bbcc.kidly.boundedcontext.settlement;

import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.CONFIRMED_AT;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.CUTOFF_AT;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.FEE_RATE;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.NOW;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.PERIOD_START;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementCalculator;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementPeriod;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSales;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementCalculatorTest {

    private static final SettlementPeriod PERIOD = SettlementPeriod.from(CUTOFF_AT);

    private final SettlementCalculator calculator = new SettlementCalculator(FEE_RATE, PERIOD);

    @Test
    @DisplayName("판매자의 대기 항목 판매 합계를 구한다")
    void sumSales() {
        var items = List.of(
            SettlementFixture.item(1L, SELLER_ID, 10_000L, CONFIRMED_AT),
            SettlementFixture.item(2L, SELLER_ID, 5_009L, CONFIRMED_AT));

        assertThat(calculator.sumSales(items).totalAmount()).isEqualTo(15_009L);
    }

    @Test
    @DisplayName("금 · 토 · 일에 확정된 항목을 월요일에 정산하면 시작일은 금요일이다")
    void startOnIsEarliestConfirmedDate() {
        var monday = new SettlementCalculator(FEE_RATE, SettlementPeriod.daily(LocalDate.of(2026, 11, 2)));
        var items = List.of(
            SettlementFixture.item(1L, SELLER_ID, 1_000L, LocalDateTime.of(2026, 10, 31, 12, 0)),
            SettlementFixture.item(2L, SELLER_ID, 1_000L, LocalDateTime.of(2026, 10, 30, 23, 59)),
            SettlementFixture.item(3L, SELLER_ID, 1_000L, LocalDateTime.of(2026, 11, 1, 23, 59, 59)));

        assertThat(monday.sumSales(items)).isEqualTo(new SettlementSales(3_000L, LocalDate.of(2026, 10, 30)));
    }

    @Test
    @DisplayName("늦게 도착한 지난주 수요일 확정 항목이 섞이면 시작일은 그 수요일이다")
    void startOnIncludesLateArrival() {
        var monday = new SettlementCalculator(FEE_RATE, SettlementPeriod.daily(LocalDate.of(2026, 11, 2)));
        var items = List.of(
            SettlementFixture.item(1L, SELLER_ID, 1_000L, LocalDateTime.of(2026, 10, 30, 10, 0)),
            SettlementFixture.item(2L, SELLER_ID, 1_000L, LocalDateTime.of(2026, 10, 28, 0, 0)));

        assertThat(monday.sumSales(items).periodStartOn()).isEqualTo(LocalDate.of(2026, 10, 28));
    }

    @Test
    @DisplayName("판매 합계 · 시작일로 기준일 전날까지의 정산서를 만들고 수수료는 원 단위 아래를 버린다")
    void create() {
        var settlement = calculator.create(SELLER_ID, new SettlementSales(15_009L, LocalDate.of(2026, 10, 30)), NOW);

        assertThat(settlement.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(settlement.getPeriodStartOn()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(settlement.getPeriodEndOn()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(settlement.getTotalSalesAmount()).isEqualTo(15_009L);
        assertThat(settlement.getFeeAmount()).isEqualTo(1_500L);
        assertThat(settlement.getSettlementAmount()).isEqualTo(13_509L);
        assertThat(settlement.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("정산 대상이 없으면 합계를 구할 수 없다")
    void sumRejectsEmpty() {
        assertThatThrownBy(() -> calculator.sumSales(List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("판매자가 섞여 있으면 합계를 구할 수 없다")
    void sumRejectsMixedSeller() {
        var items = List.of(
            SettlementFixture.item(1L, 10L, 10_000L, CONFIRMED_AT),
            SettlementFixture.item(2L, 11L, 10_000L, CONFIRMED_AT));

        assertThatThrownBy(() -> calculator.sumSales(items))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("대기 상태가 아닌 항목이 있으면 합계를 구할 수 없다")
    void sumRejectsNotPending() {
        var settled = SettlementFixture.item(2L, SELLER_ID, 10_000L, CONFIRMED_AT);
        settled.settle(7L);
        var items = List.of(SettlementFixture.item(1L, SELLER_ID, 10_000L, CONFIRMED_AT), settled);

        assertThatThrownBy(() -> calculator.sumSales(items))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("종료 기준일과 같은 시각에 확정된 항목이 있으면 합계를 구할 수 없다")
    void sumRejectsConfirmedAtCutoff() {
        var items = List.of(SettlementFixture.item(1L, SELLER_ID, 10_000L, CUTOFF_AT));

        assertThatThrownBy(() -> calculator.sumSales(items))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("판매 합계가 long을 넘으면 ArithmeticException이다")
    void sumRejectsOverflow() {
        var items = List.of(
            SettlementFixture.item(1L, SELLER_ID, Long.MAX_VALUE, CONFIRMED_AT),
            SettlementFixture.item(2L, SELLER_ID, Long.MAX_VALUE, CONFIRMED_AT));

        assertThatThrownBy(() -> calculator.sumSales(items))
            .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("수수료율이나 정산 기간이 없으면 계산기를 만들 수 없다")
    void constructRejectsNull() {
        assertThatThrownBy(() -> new SettlementCalculator(null, PERIOD)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SettlementCalculator(FEE_RATE, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("필수 값이 없으면 합계를 구하거나 정산서를 만들 수 없다")
    void rejectsNull() {
        var items = List.of(SettlementFixture.item(1L, SELLER_ID, 10_000L, CONFIRMED_AT));

        assertThatThrownBy(() -> calculator.sumSales(null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> calculator.create(SELLER_ID, null, NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> calculator.create(SELLER_ID, new SettlementSales(10_000L, PERIOD_START), null))
            .isInstanceOf(NullPointerException.class);
    }
}
