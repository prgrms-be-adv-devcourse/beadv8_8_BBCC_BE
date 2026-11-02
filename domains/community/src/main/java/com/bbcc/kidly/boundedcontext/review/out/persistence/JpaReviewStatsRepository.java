package com.bbcc.kidly.boundedcontext.review.out.persistence;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewStatsCounter;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewStatsReader;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewStats;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 상품별 리뷰 집계. 증감은 한 문장씩이다. 새 리뷰는 행이 없으면 넣고 있으면 더하는 upsert, 수정 · 삭제는 있는 행을 그 자리에서 바꾸는
 * UPDATE다(감소를 upsert로 하면 넣으려던 음수 행이 CHECK(0 이상)에 먼저 걸린다).
 */
public interface JpaReviewStatsRepository
    extends JpaRepository<ReviewStats, Long>, ReviewStatsCounter, ReviewStatsReader {

    @Override
    @Modifying
    @Query(nativeQuery = true, value = """
        insert into community.review_stats (product_id, review_count, rating_sum, created_at, updated_at)
        values (:productId, 1, :rating, :now, :now)
        on conflict (product_id) do update
        set review_count = community.review_stats.review_count + 1,
            rating_sum = community.review_stats.rating_sum + excluded.rating_sum,
            updated_at = excluded.updated_at
        """)
    void addReview(@Param("productId") Long productId, @Param("rating") int rating, @Param("now") LocalDateTime now);

    @Override
    @Modifying
    @Query(nativeQuery = true, value = """
        update community.review_stats
        set review_count = review_count + :countDelta,
            rating_sum = rating_sum + :ratingDelta,
            updated_at = :now
        where product_id = :productId
        """)
    int adjust(@Param("productId") Long productId, @Param("countDelta") int countDelta,
        @Param("ratingDelta") int ratingDelta, @Param("now") LocalDateTime now);
}
