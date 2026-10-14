
package com.bbcc.kidly.boundedcontext.settlement.domain;

public record SettlementCalculation(
        long feeAmount,
        long settlementAmount
) {
    public SettlementCalculation {
        if (feeAmount < 0 || settlementAmount < 0) {
            throw new IllegalArgumentException(
                    "수수료와 정산금액은 음수일 수 없습니다."
            );
        }
    }
}
