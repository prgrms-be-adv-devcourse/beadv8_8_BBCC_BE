package com.bbcc.kidly.boundedcontext.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;

/**
 * 회원 이용 정지 기록(값 객체). 정지된 회원만 갖는다.
 */
@Embeddable
public record MemberSuspension(
    @Column(name = "suspend_reason", length = MemberPolicy.SUSPEND_REASON_MAX_LENGTH)
    String reason,
    @Column(name = "suspended_by")
    Long suspendedBy,
    @Column(name = "suspended_at")
    LocalDateTime suspendedAt) {
}
