package com.bbcc.kidly.boundedcontext.settlement.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SettlementCreateInfo(
    Long sellerId, LocalDate periodStartOn, LocalDate periodEndOn, Long totalSalesAmount, BigDecimal feeRate) {
}
