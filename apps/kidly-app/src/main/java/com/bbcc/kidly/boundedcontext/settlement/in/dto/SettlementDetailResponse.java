package com.bbcc.kidly.boundedcontext.settlement.in.dto;

import com.bbcc.kidly.boundedcontext.settlement.app.dto.SettlementDetailResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SettlementDetailResponse(
    Long settlementId, LocalDate periodStartOn, LocalDate periodEndOn, long totalSalesAmount, BigDecimal feeRate,
    long feeAmount, long settlementAmount, String status, LocalDateTime completedAt, LocalDateTime paidAt,
    String failReason, List<Item> items) {

    public static SettlementDetailResponse from(SettlementDetailResult result) {
        return new SettlementDetailResponse(
            result.settlementId(), result.periodStartOn(), result.periodEndOn(), result.totalSalesAmount(),
            result.feeRate(), result.feeAmount(), result.settlementAmount(), result.status(),
            result.completedAt(), result.paidAt(), result.failReason(),
            result.items().stream().map(Item::from).toList());
    }

    public record Item(Long orderItemId, Long orderId, long salesAmount, LocalDateTime confirmedAt) {

        static Item from(SettlementDetailResult.Item item) {
            return new Item(item.orderItemId(), item.orderId(), item.salesAmount(), item.confirmedAt());
        }
    }
}
