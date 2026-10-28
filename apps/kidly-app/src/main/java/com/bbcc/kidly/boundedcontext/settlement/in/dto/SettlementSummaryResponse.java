package com.bbcc.kidly.boundedcontext.settlement.in.dto;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSummaryRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SettlementSummaryResponse(
    Long settlementId, LocalDate periodStartOn, LocalDate periodEndOn, long totalSalesAmount, BigDecimal feeRate,
    long feeAmount, long settlementAmount, String status, LocalDateTime completedAt, LocalDateTime paidAt) {

    public static SettlementSummaryResponse from(SettlementSummaryRow row) {
        return new SettlementSummaryResponse(
            row.settlementId(), row.periodStartOn(), row.periodEndOn(), row.totalSalesAmount(), row.feeRate(),
            row.feeAmount(), row.settlementAmount(), row.status(), row.completedAt(), row.paidAt());
    }
}
