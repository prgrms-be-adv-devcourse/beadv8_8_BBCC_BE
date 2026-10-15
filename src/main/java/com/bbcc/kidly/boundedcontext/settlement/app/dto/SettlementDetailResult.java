package com.bbcc.kidly.boundedcontext.settlement.app.dto;

import com.bbcc.kidly.boundedcontext.settlement.domain.Settlement;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SettlementDetailResult(
    Long settlementId, LocalDate periodStartOn, LocalDate periodEndOn, long totalSalesAmount, BigDecimal feeRate,
    long feeAmount, long settlementAmount, String status, LocalDateTime completedAt, LocalDateTime paidAt,
    String failReason, List<Item> items) {

    public static SettlementDetailResult of(Settlement settlement, List<SettlementItem> items) {
        return new SettlementDetailResult(
            settlement.getId(), settlement.getPeriodStartOn(), settlement.getPeriodEndOn(),
            settlement.getTotalSalesAmount(), settlement.getFeeRate(), settlement.getFeeAmount(),
            settlement.getSettlementAmount(), settlement.getStatus().name(), settlement.getCompletedAt(),
            settlement.getPaidAt(), settlement.getFailReason(), items.stream().map(Item::from).toList());
    }

    public record Item(Long orderItemId, Long orderId, long salesAmount, LocalDateTime confirmedAt) {

        static Item from(SettlementItem item) {
            return new Item(item.getOrderItemId(), item.getOrderId(), item.getSalesAmount(), item.getConfirmedAt());
        }
    }
}
