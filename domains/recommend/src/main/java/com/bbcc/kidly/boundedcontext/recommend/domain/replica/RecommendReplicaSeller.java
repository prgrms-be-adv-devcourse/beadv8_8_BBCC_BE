package com.bbcc.kidly.boundedcontext.recommend.domain.replica;

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

/**
 * 추천이 자녀 맞춤 목록에 상호명을 보여 주려고 판매자 정보 변경됨 이벤트에서 옮겨 둔 판매자 정보다.
 * 상품 도메인에도 판매자 복사본 엔티티가 있어서, JPA 엔티티 이름이 겹치지 않게 클래스 이름 앞에 도메인 이름을 붙인다.
 */
@Entity
@Table(schema = "recommend", name = "replica_seller")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendReplicaSeller extends BaseTimeEntity {

    // 원래 판매자 ID를 그대로 넣으므로 @GeneratedValue를 쓰지 않는다
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

    public static RecommendReplicaSeller create(ChangeInfo info) {
        requireValues(info);
        RecommendReplicaSeller seller = new RecommendReplicaSeller();
        seller.sellerId = requireNonNull(info.sellerId(), "sellerId는 필수입니다");
        seller.businessName = info.businessName();
        seller.status = info.status();
        seller.eventOccurredAt = info.occurredAt();
        return seller;
    }

    /**
     * 저장된 시각보다 늦게 일어난 변경만 덮어쓴다. 같은 이벤트를 다시 받거나 이전 이벤트가 늦게 도착해도 값이 되돌아가지 않는다.
     */
    public void applyIfNewer(ChangeInfo info) {
        requireValues(info);
        if (!info.occurredAt().isAfter(eventOccurredAt)) {
            return;
        }
        this.businessName = info.businessName();
        this.status = info.status();
        this.eventOccurredAt = info.occurredAt();
    }

    // 값을 하나도 바꾸기 전에 검사해서, 실패하면 복사본이 그대로 남도록 한다
    private static void requireValues(ChangeInfo info) {
        requireNonNull(info, "info는 필수입니다");
        requireNonNull(info.businessName(), "businessName은 필수입니다");
        requireNonNull(info.status(), "status는 필수입니다");
        requireNonNull(info.occurredAt(), "occurredAt은 필수입니다");
    }
}
