package com.bbcc.kidly.boundedcontext.notification.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매자 상태 복사본(판매자당 1행). seller.changed의 지금 값을 덮어쓰고, 상태가 바뀌었는지 알려 준다.
 * 발생 시각은 DB 정밀도(마이크로초)로 잘라 저장 · 비교한다. 자르지 않으면 같은 메시지를 다시 받았을 때 늦은 이벤트로 보인다.
 */
@Entity
@Table(schema = "notification", name = "seller_status")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerStatusCopy extends BaseTimeEntity {

    @Id
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SellerStatus status;

    @Column(nullable = false)
    private LocalDateTime sourceOccurredAt;

    public static SellerStatusCopy create(Long sellerId, SellerStatus status, LocalDateTime occurredAt) {
        var copy = new SellerStatusCopy();
        copy.sellerId = sellerId;
        copy.apply(status, occurredAt);
        return copy;
    }

    /**
     * 저장된 값보다 늦게 발생한 이벤트일 때만 덮어쓰고, 상태가 바뀌었으면 true를 돌려준다.
     * 같은 시각은 같은 메시지, 이른 시각은 늦게 온 옛 메시지라 무시한다(false).
     */
    public boolean changeIfNewer(SellerStatus status, LocalDateTime occurredAt) {
        if (!truncate(occurredAt).isAfter(sourceOccurredAt)) {
            return false;
        }
        var changed = this.status != status;
        apply(status, occurredAt);
        return changed;
    }

    private void apply(SellerStatus status, LocalDateTime occurredAt) {
        this.status = status;
        this.sourceOccurredAt = truncate(occurredAt);
    }

    private static LocalDateTime truncate(LocalDateTime time) {
        return time.truncatedTo(ChronoUnit.MICROS);
    }
}
