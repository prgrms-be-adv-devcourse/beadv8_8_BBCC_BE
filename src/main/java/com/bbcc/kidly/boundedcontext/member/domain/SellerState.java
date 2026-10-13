package com.bbcc.kidly.boundedcontext.member.domain;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalDateTime;

/**
 * 판매자 상태 복사본(값 객체). 판매자 변경 이벤트로 채우고, 토큰을 발급할 때 SELLER 역할을 계산하는 데 쓴다.
 */
@Embeddable
public record SellerState(
    @Enumerated(EnumType.STRING)
    @Column(name = "seller_status")
    SellerStatus status,
    @Column(name = "seller_status_changed_at")
    LocalDateTime changedAt) {

    public SellerState {
        requireNonNull(status, "status는 필수입니다");
        requireNonNull(changedAt, "changedAt은 필수입니다");
    }

    public boolean isActive() {
        return status == SellerStatus.ACTIVE;
    }

    // 이벤트 순서가 뒤바뀔 수 있어서 더 늦게 일어난 변경만 반영한다
    public boolean isOlderThan(SellerState other) {
        return changedAt.isBefore(other.changedAt);
    }
}
