package com.bbcc.kidly.boundedcontext.review.app.port;

import com.bbcc.kidly.boundedcontext.review.domain.ReviewStats;
import java.util.Optional;

/**
 * 상품별 리뷰 집계 읽기. 구현은 out/persistence(Spring Data가 만든다).
 */
public interface ReviewStatsReader {

    Optional<ReviewStats> findById(Long productId);
}
