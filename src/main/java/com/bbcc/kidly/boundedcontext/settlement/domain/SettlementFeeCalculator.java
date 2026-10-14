
package com.bbcc.kidly.boundedcontext.settlement.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class SettlementFeeCalculator {

    public SettlementCalculation calculate(
            long salesAmount,
            BigDecimal feeRate
    ) {
        if (salesAmount < 0) {
            throw new IllegalArgumentException(
                    "판매금액은 음수일 수 없습니다."
            );
        }

        if (feeRate == null) {
            throw new IllegalArgumentException("수수료율은 필수입니다.");
        }

        if (feeRate.compareTo(BigDecimal.ZERO) < 0
                || feeRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "수수료율은 0 이상 1 이하여야 합니다."
            );
        }

        long feeAmount = BigDecimal.valueOf(salesAmount)
                .multiply(feeRate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        long settlementAmount = Math.subtractExact(
                salesAmount,
                feeAmount
        );

        return new SettlementCalculation(
                feeAmount,
                settlementAmount
        );
    }
}
