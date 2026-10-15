package com.bbcc.kidly.boundedcontext.settlement;

import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.CONFIRMED_AT;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.CUTOFF_AT;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.FEE_RATE;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.NOW;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementCalculator;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementPeriod;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementCalculatorTest {

    private static final SettlementPeriod PERIOD = SettlementPeriod.from(CUTOFF_AT);

    private final SettlementCalculator calculator = new SettlementCalculator(FEE_RATE);

    @Test
    @DisplayName("판매자의 대기 항목 판매 합계를 구한다")
    void sumSalesAmount() {
        var items = List.of(
            SettlementFixture.item(1L, SELLER_ID, 10_000L, CONFIRMED_AT),
            SettlementFixture.item(2L, SELLER_ID, 5_009L, CONFIRMED_AT));

        assertThat(calculator.sumSalesAmount(PERIOD, items)).isEqualTo(15_009L);
    }

    @Test
    @DisplayName("판매 합계로 정산 기간의 정산서를 만들고 수수료는 원 단위 아래를 버린다")
    void create() {
        var settlement = calculator.create(PERIOD, SELLER_ID, 15_009L, NOW);

        assertThat(settlement.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(settlement.getPeriodStartOn()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(settlement.getPeriodEndOn()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(settlement.getTotalSalesAmount()).isEqualTo(15_009L);
        assertThat(settlement.getFeeAmount()).isEqualTo(1_500L);
        assertThat(settlement.getSettlementAmount()).isEqualTo(13_509L);
        assertThat(settlement.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("정산 대상이 없으면 합계를 구할 수 없다")
    void sumRejectsEmpty() {
        assertThatThrownBy(() -> calculator.sumSalesAmount(PERIOD, List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("판매자가 섞여 있으면 합계를 구할 수 없다")
    void sumRejectsMixedSeller() {
        var items = List.of(
            SettlementFixture.item(1L, 10L, 10_000L, CONFIRMED_AT),
            SettlementFixture.item(2L, 11L, 10_000L, CONFIRMED_AT));

        assertThatThrownBy(() -> calculator.sumSalesAmount(PERIOD, items))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("대기 상태가 아닌 항목이 있으면 합계를 구할 수 없다")
    void sumRejectsNotPending() {
        var settled = SettlementFixture.item(2L, SELLER_ID, 10_000L, CONFIRMED_AT);
        settled.settle(7L);
        var items = List.of(SettlementFixture.item(1L, SELLER_ID, 10_000L, CONFIRMED_AT), settled);

        assertThatThrownBy(() -> calculator.sumSalesAmount(PERIOD, items))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("종료 기준일과 같은 시각에 확정된 항목이 있으면 합계를 구할 수 없다")
    void sumRejectsConfirmedAtCutoff() {
        var items = List.of(SettlementFixture.item(1L, SELLER_ID, 10_000L, CUTOFF_AT));

        assertThatThrownBy(() -> calculator.sumSalesAmount(PERIOD, items))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("판매 합계가 long을 넘으면 ArithmeticException이다")
    void sumRejectsOverflow() {
        var items = List.of(
            SettlementFixture.item(1L, SELLER_ID, Long.MAX_VALUE, CONFIRMED_AT),
            SettlementFixture.item(2L, SELLER_ID, Long.MAX_VALUE, CONFIRMED_AT));

        assertThatThrownBy(() -> calculator.sumSalesAmount(PERIOD, items))
            .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("수수료율이 없으면 계산기를 만들 수 없다")
    void createRejectsNullFeeRate() {
        assertThatThrownBy(() -> new SettlementCalculator(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("필수 값이 없으면 합계를 구하거나 정산서를 만들 수 없다")
    void rejectsNull() {
        var items = List.of(SettlementFixture.item(1L, SELLER_ID, 10_000L, CONFIRMED_AT));

        assertThatThrownBy(() -> calculator.sumSalesAmount(null, items))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> calculator.sumSalesAmount(PERIOD, null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> calculator.create(null, SELLER_ID, 10_000L, NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> calculator.create(PERIOD, SELLER_ID, 10_000L, null))
            .isInstanceOf(NullPointerException.class);
    }
}
