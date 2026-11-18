package com.bbcc.kidly.boundedcontext.settlement.domain;

import java.time.LocalDate;

/**
 * @param periodStartOn 대상 중 가장 이른 확정일. 늦게 도착한 지난 확정분이 섞이면 그날이다
 */
public record SettlementSales(long totalAmount, LocalDate periodStartOn) {
}
