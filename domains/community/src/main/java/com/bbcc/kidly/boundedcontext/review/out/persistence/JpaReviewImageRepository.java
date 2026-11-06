package com.bbcc.kidly.boundedcontext.review.out.persistence;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImages;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 리뷰 사진 저장소. 리뷰별 조회는 유일 제약 (review_id, position)의 인덱스를 쓴다.
 */
public interface JpaReviewImageRepository extends JpaRepository<ReviewImage, Long>, ReviewImages {

    @Override
    @Modifying
    @Query("delete from CommunityReviewImage i where i.reviewId = :reviewId")
    int deleteAllOfReview(@Param("reviewId") Long reviewId);
}
