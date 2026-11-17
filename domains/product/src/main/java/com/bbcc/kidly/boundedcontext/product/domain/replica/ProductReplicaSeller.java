package com.bbcc.kidly.boundedcontext.product.domain.replica;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "product", name = "replica_seller")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductReplicaSeller extends BaseTimeEntity {

    @Id
    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SellerStatus status;

    @Column(name = "event_occurred_at", nullable = false)
    private LocalDateTime eventOccurredAt;

    public record ChangeInfo(Long sellerId, String businessName, SellerStatus status, LocalDateTime occurredAt) {
    }

    public static ProductReplicaSeller create(ChangeInfo info) {
        requireValues(info);
        var seller = new ProductReplicaSeller();
        seller.sellerId = requireNonNull(info.sellerId(), "sellerId는 필수입니다");
        seller.businessName = info.businessName();
        seller.status = info.status();
        seller.eventOccurredAt = info.occurredAt();
        return seller;
    }

    public void applyIfNewer(ChangeInfo info) {
        requireNonNull(info, "info는 필수입니다");
        requireNonNull(info.occurredAt(), "occurredAt은 필수입니다");
        if (!info.occurredAt().isAfter(eventOccurredAt)) {
            return;
        }
        requireValues(info);
        this.businessName = info.businessName();
        this.status = info.status();
        this.eventOccurredAt = info.occurredAt();
    }

    private static void requireValues(ChangeInfo info) {
        requireNonNull(info, "info는 필수입니다");
        requireNonNull(info.businessName(), "businessName은 필수입니다");
        requireNonNull(info.status(), "status는 필수입니다");
        requireNonNull(info.occurredAt(), "occurredAt은 필수입니다");
    }
}
