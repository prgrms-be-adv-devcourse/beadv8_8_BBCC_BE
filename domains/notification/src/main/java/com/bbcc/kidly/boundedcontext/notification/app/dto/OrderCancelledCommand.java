package com.bbcc.kidly.boundedcontext.notification.app.dto;

public record OrderCancelledCommand(Long orderId, Long memberId, String reason) {
}
