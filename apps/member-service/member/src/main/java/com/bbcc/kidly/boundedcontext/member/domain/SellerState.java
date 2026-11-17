package com.bbcc.kidly.boundedcontext.member.domain;

import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalDateTime;

@Embeddable
public record SellerState(
    @Enumerated(EnumType.STRING)
    @Column(name = "seller_status")
    SellerStatus status,
    @Column(name = "seller_status_changed_at")
    LocalDateTime changedAt) {

    public boolean isActive() {
        return status == SellerStatus.ACTIVE;
    }

    public boolean isClosed() {
        return status == SellerStatus.CLOSED;
    }

    public boolean isOlderThan(SellerState other) {
        return changedAt.isBefore(other.changedAt);
    }
}
