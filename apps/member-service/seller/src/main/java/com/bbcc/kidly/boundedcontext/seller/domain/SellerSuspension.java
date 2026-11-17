package com.bbcc.kidly.boundedcontext.seller.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;

@Embeddable
public record SellerSuspension(
    @Column(name = "suspend_reason", length = SellerPolicy.SUSPEND_REASON_MAX_LENGTH)
    String reason,
    @Column(name = "suspended_by")
    Long suspendedBy,
    @Column(name = "suspended_at")
    LocalDateTime suspendedAt) {
}
