package com.bbcc.kidly.boundedcontext.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리뷰 작성 자격(주문상품당 1행). 주문상품을 구매확정하면 생긴다. 리뷰를 쓸 수 있는 주문상품 목록이 이것을 읽는다.
 */
@Entity(name = "CommunityReviewEligibility")
@Table(schema = "community", name = "review_eligibility")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewEligibility extends ReviewTimeEntity {

    /**
     * 자격 입력 값.
     *
     * @param orderId 주문 ID. 없으면 null
     * @param optionId 옵션 ID. 없으면 null
     * @param size 사이즈. 사이즈 없는 상품은 null
     * @param confirmedAt 구매확정 시각. 목록 순서의 기준이다
     */
    public record Values(
        Long orderItemId,
        Long orderId,
        Long memberId,
        Long productId,
        Long optionId,
        String size,
        LocalDateTime confirmedAt) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long orderItemId;

    private Long orderId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long productId;

    private Long optionId;

    // 사이즈 문자열(varchar 255). 이벤트로 받은 값을 자르지 않고 그대로 담는다
    private String size;

    @Column(nullable = false)
    private LocalDateTime confirmedAt;

    public static ReviewEligibility create(Values values) {
        ReviewEligibility eligibility = new ReviewEligibility();
        eligibility.orderItemId = values.orderItemId();
        eligibility.orderId = values.orderId();
        eligibility.memberId = values.memberId();
        eligibility.productId = values.productId();
        eligibility.optionId = values.optionId();
        eligibility.size = values.size();
        eligibility.confirmedAt = values.confirmedAt();
        return eligibility;
    }
}
