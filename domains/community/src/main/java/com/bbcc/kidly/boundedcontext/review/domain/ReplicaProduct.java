package com.bbcc.kidly.boundedcontext.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 복사본(상품당 1행). product.changed의 지금 값을 덮어쓰며, 리뷰 · 찜 화면에 상품 이름 · 썸네일 · 표시 상태를 보여 줄 때 쓴다.
 * 옵션은 저장하지 않고 표시 상태 계산에만 쓴다.
 * 엔티티 이름은 한 앱 안에서 겹치면 안 되므로(다른 도메인에 같은 클래스 이름이 있을 수 있다) CommunityReplicaProduct로 둔다.
 */
@Entity(name = "CommunityReplicaProduct")
@Table(schema = "community", name = "replica_product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplicaProduct extends ReviewTimeEntity {

    /**
     * 복사본 입력 값.
     *
     * @param productStatus 상품 판매 상태 이름. 모르는 값 · null이면 판매 종료
     * @param options 옵션 판매 상태 · 품절 여부. 표시 상태 계산에만 쓴다
     * @param occurredAt 이벤트 발생 시각. 저장된 값보다 늦을 때만 반영한다
     */
    public record Values(
        Long productId,
        String name,
        String thumbnailUrl,
        String productStatus,
        List<OptionState> options,
        LocalDateTime occurredAt) {
    }

    /**
     * @param saleStatus 옵션 판매 상태 이름. 판매 중(ON_SALE)이 아니면(모르는 값 · null 포함) 표시 상태 계산에서 보지 않는다
     */
    public record OptionState(String saleStatus, boolean soldOut) {
    }

    @Id
    private Long productId;

    @Column(nullable = false)
    private String name;

    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DisplayStatus displayStatus;

    @Column(nullable = false)
    private LocalDateTime sourceOccurredAt;

    public static ReplicaProduct create(Values values) {
        ReplicaProduct replica = new ReplicaProduct();
        replica.productId = values.productId();
        replica.apply(values);
        return replica;
    }

    /**
     * 저장된 값보다 늦게 발생한 이벤트일 때만 덮어쓴다(같은 시각 = 중복, 이른 시각 = 늦게 온 옛 이벤트는 무시).
     * 시각은 마이크로초로 잘라 비교 · 저장한다(ReviewTimes).
     *
     * @return 반영했으면 true
     */
    public boolean applyIfNewer(Values values) {
        if (!ReviewTimes.toStoredPrecision(values.occurredAt()).isAfter(sourceOccurredAt)) {
            return false;
        }
        apply(values);
        return true;
    }

    private void apply(Values values) {
        name = values.name();
        thumbnailUrl = values.thumbnailUrl();
        displayStatus = DisplayStatus.of(values.productStatus(), values.options());
        sourceOccurredAt = ReviewTimes.toStoredPrecision(values.occurredAt());
    }
}
