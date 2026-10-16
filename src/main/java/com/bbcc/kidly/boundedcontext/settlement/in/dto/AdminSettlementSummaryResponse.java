package com.bbcc.kidly.boundedcontext.settlement.in.dto;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSummaryRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 관리자 정산서 목록 한 줄. 판매자 응답에 판매자 ID와 지급 실패 사유(실패 전에는 null)를 더한다.
 */
public record AdminSettlementSummaryResponse(
    Long settlementId, Long sellerId, LocalDate periodStartOn, LocalDate periodEndOn, long totalSalesAmount,
    BigDecimal feeRate, long feeAmount, long settlementAmount, String status, LocalDateTime completedAt,
    LocalDateTime paidAt, String failReason) {

    public static AdminSettlementSummaryResponse from(SettlementSummaryRow row) {
        return new AdminSettlementSummaryResponse(
            row.settlementId(), row.sellerId(), row.periodStartOn(), row.periodEndOn(), row.totalSalesAmount(),
            row.feeRate(), row.feeAmount(), row.settlementAmount(), row.status(), row.completedAt(), row.paidAt(),
            row.failReason());
    }
}
