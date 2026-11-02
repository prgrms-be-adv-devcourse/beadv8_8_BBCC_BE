package com.bbcc.kidly.boundedcontext.review.out.persistence;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewEligibilities;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewEligibility;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 커서 조건은 (구매확정 시각, id) 튜플 비교라 인덱스 (member_id, confirmed_at desc, id desc)의 범위 조건으로 쓰인다.
 */
public interface JpaReviewEligibilityRepository extends JpaRepository<ReviewEligibility, Long>, ReviewEligibilities {

    String ELIGIBLE_SELECT = """
        select e.id as id, e.orderItemId as orderItemId, e.orderId as orderId, e.productId as productId,
               p.name as productName, p.thumbnailUrl as thumbnailUrl, e.size as size, e.confirmedAt as confirmedAt
        from CommunityReviewEligibility e
        left join CommunityReplicaProduct p on p.productId = e.productId
        where e.memberId = :memberId
        """;
    String ELIGIBLE_ORDER = " order by e.confirmedAt desc, e.id desc";

    @Override
    @Query(ELIGIBLE_SELECT + ELIGIBLE_ORDER)
    List<EligibleRow> findEligible(@Param("memberId") Long memberId, Limit limit);

    @Override
    @Query(ELIGIBLE_SELECT
        + " and (e.confirmedAt, e.id) < (:confirmedAt, :id)" + ELIGIBLE_ORDER)
    List<EligibleRow> findEligibleAfter(@Param("memberId") Long memberId,
        @Param("confirmedAt") LocalDateTime confirmedAt, @Param("id") Long id, Limit limit);
}
