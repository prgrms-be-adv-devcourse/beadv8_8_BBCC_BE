package com.bbcc.kidly.boundedcontext.seller.domain;

import static java.util.Objects.requireNonNull;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;
import org.springframework.util.Assert;

/**
 * 판매자 자격 정지 기록(값 객체). 정지된 판매자만 갖는다. 사유는 판매자에게 알리는 데 쓴다.
 */
@Embeddable
public record SellerSuspension(
    @Column(name = "suspend_reason", length = SellerSuspension.REASON_MAX_LENGTH)
    String reason,
    @Column(name = "suspended_by")
    Long suspendedBy,
    @Column(name = "suspended_at")
    LocalDateTime suspendedAt) {

    public static final int REASON_MAX_LENGTH = 200;

    public SellerSuspension {
        requireNonNull(reason, "reason은 필수입니다");
        requireNonNull(suspendedBy, "suspendedBy는 필수입니다");
        requireNonNull(suspendedAt, "suspendedAt은 필수입니다");
        Assert.hasText(reason, "정지 사유는 비어 있을 수 없습니다");
        Assert.isTrue(reason.length() <= REASON_MAX_LENGTH, "정지 사유는 200자 이하여야 합니다");
    }
}
