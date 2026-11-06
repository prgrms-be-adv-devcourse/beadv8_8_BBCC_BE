package com.bbcc.kidly.boundedcontext.review.app.port;

import java.time.LocalDateTime;

/**
 * 상품별 리뷰 집계 증감. 둘 다 한 문장이라 동시에 증감해도 빠지지 않는다.
 */
public interface ReviewStatsCounter {

    /**
     * 새 리뷰: 리뷰 수 +1 · 별점 합 +rating · 핏별 수(남긴 핏만 +1). 첫 리뷰면 행을 만든다(INSERT … ON CONFLICT DO UPDATE).
     */
    void addReview(Long productId, int rating, int fitSmall, int fitTrueToSize, int fitLarge, LocalDateTime now);

    /**
     * 수정 · 삭제: 있는 집계 행을 그 자리에서 바꾼다(UPDATE … SET x = x + :delta).
     *
     * @return 바뀐 행 수. 0이면 집계 행이 없다(리뷰가 있는데 집계가 없는 어긋난 상태)
     */
    int adjust(Long productId, int countDelta, int ratingDelta, int fitSmall, int fitTrueToSize, int fitLarge,
        LocalDateTime now);
}
