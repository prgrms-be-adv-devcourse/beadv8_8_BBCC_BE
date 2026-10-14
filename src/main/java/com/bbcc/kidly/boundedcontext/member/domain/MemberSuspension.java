package com.bbcc.kidly.boundedcontext.member.domain;

import static java.util.Objects.requireNonNull;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;
import org.springframework.util.Assert;

/**
 * 회원 이용 정지 기록(값 객체). 정지된 회원만 갖는다.
 */
@Embeddable
public record MemberSuspension(
    @Column(name = "suspend_reason", length = MemberSuspension.REASON_MAX_LENGTH)
    String reason,
    @Column(name = "suspended_by")
    Long suspendedBy,
    @Column(name = "suspended_at")
    LocalDateTime suspendedAt) {

    public static final int REASON_MAX_LENGTH = 200;

    public MemberSuspension {
        requireNonNull(reason, "reason은 필수입니다");
        requireNonNull(suspendedBy, "suspendedBy는 필수입니다");
        requireNonNull(suspendedAt, "suspendedAt은 필수입니다");
        Assert.hasText(reason, "정지 사유는 비어 있을 수 없습니다");
        Assert.isTrue(reason.length() <= REASON_MAX_LENGTH, "정지 사유는 200자 이하여야 합니다");
    }
}
