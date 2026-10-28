package com.bbcc.kidly.boundedcontext.settlement.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 판매자 한 명의 정산서를 만드는 입력. 검증은 Settlement가 한다.
 */
public record SettlementCreateInfo(
    Long sellerId, LocalDate periodStartOn, LocalDate periodEndOn, Long totalSalesAmount, BigDecimal feeRate) {
}
