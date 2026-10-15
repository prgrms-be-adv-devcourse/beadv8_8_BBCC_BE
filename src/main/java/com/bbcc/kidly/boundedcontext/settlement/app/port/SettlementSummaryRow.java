package com.bbcc.kidly.boundedcontext.settlement.app.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SettlementSummaryRow(
    Long settlementId, LocalDate periodStartOn, LocalDate periodEndOn, long totalSalesAmount, BigDecimal feeRate,
    long feeAmount, long settlementAmount, String status, LocalDateTime completedAt, LocalDateTime paidAt) {
}
