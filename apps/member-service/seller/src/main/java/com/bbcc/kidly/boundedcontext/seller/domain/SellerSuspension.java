package com.bbcc.kidly.boundedcontext.seller.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;

/**
 * 판매자 자격 정지 기록(값 객체). 정지된 판매자만 갖는다. 사유는 판매자에게 알리는 데 쓴다.
 */
@Embeddable
public record SellerSuspension(
    @Column(name = "suspend_reason", length = SellerPolicy.SUSPEND_REASON_MAX_LENGTH)
    String reason,
    @Column(name = "suspended_by")
    Long suspendedBy,
    @Column(name = "suspended_at")
    LocalDateTime suspendedAt) {
}
