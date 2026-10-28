package com.bbcc.kidly.boundedcontext.settlement.domain;

public record SettlementSkipCreateInfo(Long jobExecutionId, Long sellerId, SettlementSkipPhase phase, String reason) {
}
