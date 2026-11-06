package com.bbcc.kidly.boundedcontext.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품별 리뷰 집계(리뷰 수 · 별점 합). 값은 저장소의 원자적 증감 한 문장으로만 바꾸고, 엔티티로 읽어 더해 저장하지 않는다.
 */
@Entity(name = "CommunityReviewStats")
@Table(schema = "community", name = "review_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewStats extends ReviewTimeEntity {

    @Id
    private Long productId;

    @Column(nullable = false)
    private long reviewCount;

    @Column(nullable = false)
    private long ratingSum;

    // 핏별 리뷰 수(핏을 남긴 리뷰만 센다)
    @Column(nullable = false)
    private long fitSmall;

    @Column(nullable = false)
    private long fitTrueToSize;

    @Column(nullable = false)
    private long fitLarge;
}
