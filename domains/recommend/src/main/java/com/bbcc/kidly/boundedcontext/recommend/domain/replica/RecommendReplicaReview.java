package com.bbcc.kidly.boundedcontext.recommend.domain.replica;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
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
 * 추천이 사이즈 AI 입력의 지난 구매에 그 구매의 리뷰 핏을 붙이려고 리뷰 변경됨 이벤트에서 옮겨 둔 리뷰 정보다.
 * 리뷰가 구매확정보다 먼저 도착할 수 있어서, 구매 기록에 넣지 않고 따로 두었다가 주문상품 ID로 잇는다.
 * 다른 도메인에도 리뷰 엔티티가 있을 수 있어서, JPA 엔티티 이름이 겹치지 않게 클래스 이름 앞에 도메인 이름을 붙인다.
 */
@Entity
@Table(schema = "recommend", name = "replica_review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendReplicaReview extends BaseTimeEntity {

    // 원래 리뷰 ID를 그대로 넣으므로 @GeneratedValue를 쓰지 않는다
    @Id
    @Column(name = "review_id")
    private Long reviewId;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    // 리뷰에 핏을 남기지 않을 수 있어 null을 허용한다
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ReviewFit fit;

    @Column(name = "is_visible", nullable = false)
    private boolean visible;

    @Column(name = "event_occurred_at", nullable = false)
    private LocalDateTime eventOccurredAt;

    public record ChangeInfo(Long reviewId, Long orderItemId, Long productId, ReviewFit fit, Boolean visible,
                             LocalDateTime occurredAt) {
    }

    public static RecommendReplicaReview create(ChangeInfo info) {
        requireValues(info);
        RecommendReplicaReview review = new RecommendReplicaReview();
        review.reviewId = info.reviewId();
        review.orderItemId = info.orderItemId();
        review.productId = info.productId();
        review.fit = info.fit();
        review.visible = info.visible();
        review.eventOccurredAt = info.occurredAt();
        return review;
    }

    /**
     * 저장된 시각보다 늦은 리뷰 변경만 반영한다. 숨긴 뒤 늦게 도착한 예전 공개 이벤트가 핏을 되살리지 못하게 시각을 먼저 비교한다.
     */
    public void applyIfNewer(ChangeInfo info) {
        requireNonNull(info, "info는 필수입니다");
        requireNonNull(info.occurredAt(), "occurredAt은 필수입니다");
        if (!info.occurredAt().isAfter(eventOccurredAt)) {
            return;
        }
        requireValues(info);
        this.fit = info.fit();
        this.visible = info.visible();
        this.eventOccurredAt = info.occurredAt();
    }

    /**
     * 사이즈 AI 입력에 쓸 핏이다. 숨기거나 삭제한 리뷰는 핏이 있어도 쓰지 않는다.
     */
    public ReviewFit countedFit() {
        if (!visible) {
            return null;
        }
        return fit;
    }

    // fit만 비어 있어도 된다
    private static void requireValues(ChangeInfo info) {
        requireNonNull(info, "info는 필수입니다");
        requireNonNull(info.reviewId(), "reviewId는 필수입니다");
        requireNonNull(info.orderItemId(), "orderItemId는 필수입니다");
        requireNonNull(info.productId(), "productId는 필수입니다");
        requireNonNull(info.visible(), "visible은 필수입니다");
        requireNonNull(info.occurredAt(), "occurredAt은 필수입니다");
    }
}
