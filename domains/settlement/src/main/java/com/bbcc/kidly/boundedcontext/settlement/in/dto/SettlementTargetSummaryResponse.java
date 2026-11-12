package com.bbcc.kidly.boundedcontext.settlement.in.dto;

/**
 * @param totalSalesAmount 아직 정산서에 들어가지 않은 구매확정분 전체의 판매금액 합계(수수료 전)
 */
public record SettlementTargetSummaryResponse(long totalSalesAmount) {
}
