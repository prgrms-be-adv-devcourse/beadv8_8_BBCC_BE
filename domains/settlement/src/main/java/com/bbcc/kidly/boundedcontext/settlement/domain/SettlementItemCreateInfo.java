package com.bbcc.kidly.boundedcontext.settlement.domain;

import java.time.LocalDateTime;

public record SettlementItemCreateInfo(
        Long orderId,
        Long orderItemId,
        Long sellerId,
        Long salesAmount,
        LocalDateTime confirmedAt) {
}
