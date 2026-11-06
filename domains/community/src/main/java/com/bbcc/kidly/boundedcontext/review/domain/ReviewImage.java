package com.bbcc.kidly.boundedcontext.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리뷰 사진 한 장(리뷰 안의 순서 · 공개 주소). 리뷰를 지우면 DB가 함께 지운다(ON DELETE CASCADE).
 * 사진을 바꿀 때는 그 리뷰의 행을 모두 지우고 새로 넣는다(행을 고치지 않는다).
 */
@Entity(name = "CommunityReviewImage")
@Table(schema = "community", name = "review_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewImage {

    static final int MAX_URL_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reviewId;

    // 표 컬럼이 smallint라 short로 둔다. 밖에는 int로 보인다(getPosition)
    @Getter(AccessLevel.NONE)
    @Column(nullable = false)
    private short position;

    @Column(nullable = false, length = MAX_URL_LENGTH)
    private String imageUrl;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ReviewImage of(Long reviewId, int position, String imageUrl) {
        ReviewImage image = new ReviewImage();
        image.reviewId = reviewId;
        image.position = (short) position;
        image.imageUrl = imageUrl;
        return image;
    }

    public int getPosition() {
        return position;
    }

    @PrePersist
    void recordCreated() {
        this.createdAt = ReviewTimes.now();
    }
}
