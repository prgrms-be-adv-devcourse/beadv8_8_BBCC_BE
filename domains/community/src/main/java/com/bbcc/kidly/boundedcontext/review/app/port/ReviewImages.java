package com.bbcc.kidly.boundedcontext.review.app.port;

import com.bbcc.kidly.boundedcontext.review.domain.ReviewImage;
import java.util.Collection;
import java.util.List;

/**
 * 리뷰 사진 저장소. 구현은 out/persistence(Spring Data가 만든다).
 */
public interface ReviewImages {

    <S extends ReviewImage> List<S> saveAll(Iterable<S> images);

    List<ReviewImage> findByReviewIdOrderByPositionAsc(Long reviewId);

    /**
     * 목록 한 쪽의 사진을 한 번에 읽는다(리뷰 ID · 순서 차례). 쪽마다 쿼리 1번이고 사진 수와 상관없다.
     */
    List<ReviewImage> findByReviewIdInOrderByReviewIdAscPositionAsc(Collection<Long> reviewIds);

    /**
     * 리뷰의 사진 행을 한 문장으로 모두 지운다(사진 교체 때 먼저 지우고 새로 넣는다).
     */
    int deleteAllOfReview(Long reviewId);
}
