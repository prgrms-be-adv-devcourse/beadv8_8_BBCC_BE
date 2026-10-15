
package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementCalculation;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementFeeCalculator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SettlementFeeCalculatorTest {

    private final SettlementFeeCalculator calculator =
            new SettlementFeeCalculator();

    @Test
    void calculatesFeeAndSettlementAmount() {
        SettlementCalculation result =
                calculator.calculate(10_000L, new BigDecimal("0.03"));

        assertThat(result.feeAmount()).isEqualTo(300L);
        assertThat(result.settlementAmount()).isEqualTo(9_700L);
    }

    @Test
    void roundsFeeToNearestWon() {
        SettlementCalculation result =
                calculator.calculate(1_015L, new BigDecimal("0.03"));

        assertThat(result.feeAmount()).isEqualTo(30L);
        assertThat(result.settlementAmount()).isEqualTo(985L);
    }

    @Test
    void roundsHalfWonUp() {
        SettlementCalculation result =
                calculator.calculate(150L, new BigDecimal("0.01"));

        assertThat(result.feeAmount()).isEqualTo(2L);
    }

    @Test
    void returnsFullAmountForZeroFeeRate() {
        SettlementCalculation result =
                calculator.calculate(10_000L, BigDecimal.ZERO);

        assertThat(result.feeAmount()).isZero();
        assertThat(result.settlementAmount()).isEqualTo(10_000L);
    }

    @Test
    void returnsZeroSettlementForFullFeeRate() {
        SettlementCalculation result =
                calculator.calculate(10_000L, BigDecimal.ONE);

        assertThat(result.feeAmount()).isEqualTo(10_000L);
        assertThat(result.settlementAmount()).isZero();
    }

    @Test
    void returnsZeroAmountsForZeroSales() {
        SettlementCalculation result =
                calculator.calculate(0L, new BigDecimal("0.03"));

        assertThat(result.feeAmount()).isZero();
        assertThat(result.settlementAmount()).isZero();
    }

    @Test
    void rejectsNegativeSalesAmount() {
        assertThatThrownBy(() ->
                calculator.calculate(-1L, new BigDecimal("0.03")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeFeeRate() {
        assertThatThrownBy(() ->
                calculator.calculate(10_000L, new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFeeRateAboveOne() {
        assertThatThrownBy(() ->
                calculator.calculate(10_000L, new BigDecimal("1.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullFeeRate() {
        assertThatThrownBy(() ->
                calculator.calculate(10_000L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesAmountInvariants() {
        long[] amounts = {0L, 1L, 50L, 150L, 1_015L, 10_000L};

        BigDecimal[] rates = {
                BigDecimal.ZERO,
                new BigDecimal("0.01"),
                new BigDecimal("0.03"),
                new BigDecimal("0.125"),
                BigDecimal.ONE
        };

        for (long amount : amounts) {
            for (BigDecimal rate : rates) {
                SettlementCalculation result =
                        calculator.calculate(amount, rate);

                assertThat(result.feeAmount()).isGreaterThanOrEqualTo(0L);
                assertThat(result.feeAmount()).isLessThanOrEqualTo(amount);
                assertThat(result.settlementAmount())
                        .isGreaterThanOrEqualTo(0L);
                assertThat(result.feeAmount() + result.settlementAmount())
                        .isEqualTo(amount);
            }
        }
    }
}
