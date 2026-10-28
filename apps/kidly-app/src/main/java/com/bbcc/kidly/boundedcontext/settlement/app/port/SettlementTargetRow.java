package com.bbcc.kidly.boundedcontext.settlement.app.port;

import java.time.LocalDateTime;

public record SettlementTargetRow(Long orderItemId, Long orderId, long salesAmount, LocalDateTime confirmedAt) {
}
