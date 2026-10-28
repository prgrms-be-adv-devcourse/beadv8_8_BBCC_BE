package com.bbcc.kidly.boundedcontext.payment.app.port;

import java.time.LocalDateTime;

public record PgCancellation(LocalDateTime cancelledAt) {
}
