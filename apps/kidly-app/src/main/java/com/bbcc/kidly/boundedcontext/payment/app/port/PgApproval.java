package com.bbcc.kidly.boundedcontext.payment.app.port;

import java.time.LocalDateTime;

public record PgApproval(String paymentKey, long amount, LocalDateTime approvedAt) {
}
