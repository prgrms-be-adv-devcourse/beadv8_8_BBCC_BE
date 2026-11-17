package com.bbcc.kidly.boundedcontext.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;

@Embeddable
public record MemberSuspension(
    @Column(name = "suspend_reason", length = MemberPolicy.SUSPEND_REASON_MAX_LENGTH)
    String reason,
    @Column(name = "suspended_by")
    Long suspendedBy,
    @Column(name = "suspended_at")
    LocalDateTime suspendedAt) {
}
