package com.bbcc.kidly.boundedcontext.settlement.domain;

import java.time.LocalDate;

/** 월별 정산서 생성 입력. 값 검증은 SettlementStatement가 담당한다. */
public record SettlementStatementCreateInfo(
        Long sellerId,
        LocalDate periodStartOn,
        LocalDate periodEndOn,
        Long totalSalesAmount,
        Long totalFeeAmount,
        Long totalSettlementAmount) {
}
