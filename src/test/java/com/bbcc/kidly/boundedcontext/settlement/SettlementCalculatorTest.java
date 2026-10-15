package com.bbcc.kidly.boundedcontext.settlement;

import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.CONFIRMED_AT;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.CUTOFF_AT;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.FEE_RATE;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.NOW;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementCalculator;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementCalculatorTest {

    private final SettlementCalculator calculator = new SettlementCalculator();

    @Test
    @DisplayName("판매자의 대기 항목을 실행 기간의 정산서로 만든다")
    void calculate() {
        var items = List.of(
            SettlementFixture.item(1L, SELLER_ID, 10_000L, CONFIRMED_AT),
            SettlementFixture.item(2L, SELLER_ID, 5_009L, CONFIRMED_AT));

        var settlement = calculator.calculate(SettlementFixture.run(), items, FEE_RATE, NOW);

        assertThat(settlement.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(settlement.getPeriodStartOn()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(settlement.getPeriodEndOn()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(settlement.getTotalSalesAmount()).isEqualTo(15_009L);
        assertThat(settlement.getFeeAmount()).isEqualTo(1_500L);
        assertThat(settlement.getSettlementAmount()).isEqualTo(13_509L);
        assertThat(settlement.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("정산 대상이 없으면 만들 수 없다")
    void calculateRejectsEmpty() {
        assertThatThrownBy(() -> calculator.calculate(SettlementFixture.run(), List.of(), FEE_RATE, NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("판매자가 섞여 있으면 만들 수 없다")
    void calculateRejectsMixedSeller() {
        var items = List.of(
            SettlementFixture.item(1L, 10L, 10_000L, CONFIRMED_AT),
            SettlementFixture.item(2L, 11L, 10_000L, CONFIRMED_AT));

        assertThatThrownBy(() -> calculator.calculate(SettlementFixture.run(), items, FEE_RATE, NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("대기 상태가 아닌 항목이 있으면 만들 수 없다")
    void calculateRejectsNotPending() {
        var settled = SettlementFixture.item(2L, SELLER_ID, 10_000L, CONFIRMED_AT);
        settled.settle(7L);
        var items = List.of(SettlementFixture.item(1L, SELLER_ID, 10_000L, CONFIRMED_AT), settled);

        assertThatThrownBy(() -> calculator.calculate(SettlementFixture.run(), items, FEE_RATE, NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("종료 기준일 이후에 확정된 항목이 있으면 만들 수 없다")
    void calculateRejectsAfterCutoff() {
        var items = List.of(SettlementFixture.item(1L, SELLER_ID, 10_000L, CUTOFF_AT));

        assertThatThrownBy(() -> calculator.calculate(SettlementFixture.run(), items, FEE_RATE, NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("판매 합계가 long을 넘으면 만들 수 없다")
    void calculateRejectsOverflow() {
        var items = List.of(
            SettlementFixture.item(1L, SELLER_ID, Long.MAX_VALUE, CONFIRMED_AT),
            SettlementFixture.item(2L, SELLER_ID, Long.MAX_VALUE, CONFIRMED_AT));

        assertThatThrownBy(() -> calculator.calculate(SettlementFixture.run(), items, FEE_RATE, NOW))
            .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("필수 값이 없으면 만들 수 없다")
    void calculateRejectsNull() {
        var run = SettlementFixture.run();
        var items = List.of(SettlementFixture.item(1L, SELLER_ID, 10_000L, CONFIRMED_AT));

        assertThatThrownBy(() -> calculator.calculate(null, items, FEE_RATE, NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> calculator.calculate(run, null, FEE_RATE, NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> calculator.calculate(run, items, null, NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> calculator.calculate(run, items, FEE_RATE, null))
            .isInstanceOf(NullPointerException.class);
    }
}
