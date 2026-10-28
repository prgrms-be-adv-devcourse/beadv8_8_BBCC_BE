package com.bbcc.kidly.boundedcontext.settlement.in.dto;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementTargetRow;
import java.time.LocalDateTime;

public record SettlementTargetResponse(Long orderItemId, Long orderId, long salesAmount, LocalDateTime confirmedAt) {

    public static SettlementTargetResponse from(SettlementTargetRow row) {
        return new SettlementTargetResponse(row.orderItemId(), row.orderId(), row.salesAmount(), row.confirmedAt());
    }
}
