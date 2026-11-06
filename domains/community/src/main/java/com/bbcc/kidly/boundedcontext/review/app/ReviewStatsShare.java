package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.app.ReviewService.FitDelta;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewStatsCounter;
import com.bbcc.kidly.boundedcontext.review.domain.Review;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 리뷰 한 건의 몫(수 1 · 별점 · 핏)을 상품 요약 집계에서 빼거나 더한다. 공개 ↔ 숨김이 바뀐 트랜잭션 안에서 부른다.
 */
@Component
@RequiredArgsConstructor
class ReviewStatsShare {

    private final ReviewStatsCounter reviewStatsCounter;

    /**
     * 공개 → 숨김. 집계 행이 없으면(바뀐 행 0) 실패시켜 상태 변경도 되돌린다. 메시지에는 상품 ID만 넣는다.
     */
    void remove(Review review) {
        FitDelta fit = FitDelta.of(review.getFit(), null);
        if (reviewStatsCounter.adjust(review.getProductId(), -1, -review.getRating(), fit.small(), fit.trueToSize(),
            fit.large(), ReviewTimes.now()) == 0) {
            throw new IllegalStateException("리뷰 집계 행이 없습니다: productId=" + review.getProductId());
        }
    }

    /**
     * 숨김 → 공개. 그때의 별점 · 핏으로 더한다.
     */
    void add(Review review) {
        FitDelta fit = FitDelta.of(null, review.getFit());
        reviewStatsCounter.addReview(review.getProductId(), review.getRating(), fit.small(), fit.trueToSize(),
            fit.large(), ReviewTimes.now());
    }
}
