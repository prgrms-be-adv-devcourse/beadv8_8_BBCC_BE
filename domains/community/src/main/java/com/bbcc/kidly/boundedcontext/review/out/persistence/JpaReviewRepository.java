package com.bbcc.kidly.boundedcontext.review.out.persistence;

import com.bbcc.kidly.boundedcontext.review.app.port.Reviews;
import com.bbcc.kidly.boundedcontext.review.domain.Review;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 리뷰 저장소. 목록은 리뷰 left join 상품 복사본 한 쿼리이고, 커서 조건은 (작성 시각, id) 튜플 비교라
 * 인덱스 (product_id | member_id, created_at desc, id desc)의 범위 조건으로 쓰인다.
 */
public interface JpaReviewRepository extends JpaRepository<Review, Long>, Reviews {

    String LIST_SELECT = """
        select r.id as id, r.orderItemId as orderItemId, r.productId as productId, p.name as productName,
               p.thumbnailUrl as thumbnailUrl, p.displayStatus as displayStatus, r.rating as rating,
               r.content as content, r.nickname as nickname, r.createdAt as createdAt, r.updatedAt as updatedAt
        from CommunityReview r
        left join CommunityReplicaProduct p on p.productId = r.productId
        """;
    String AFTER = " and (r.createdAt, r.id) < (:createdAt, :id)";
    String NEWEST_FIRST = " order by r.createdAt desc, r.id desc";

    @Override
    @Query(LIST_SELECT + " where r.productId = :productId" + NEWEST_FIRST)
    List<ReviewListRow> findByProduct(@Param("productId") Long productId, Limit limit);

    @Override
    @Query(LIST_SELECT + " where r.productId = :productId" + AFTER + NEWEST_FIRST)
    List<ReviewListRow> findByProductAfter(@Param("productId") Long productId,
        @Param("createdAt") LocalDateTime createdAt, @Param("id") Long id, Limit limit);

    @Override
    @Query(LIST_SELECT + " where r.memberId = :memberId" + NEWEST_FIRST)
    List<ReviewListRow> findByMember(@Param("memberId") Long memberId, Limit limit);

    @Override
    @Query(LIST_SELECT + " where r.memberId = :memberId" + AFTER + NEWEST_FIRST)
    List<ReviewListRow> findByMemberAfter(@Param("memberId") Long memberId,
        @Param("createdAt") LocalDateTime createdAt, @Param("id") Long id, Limit limit);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CommunityReview r where r.id = :reviewId")
    Optional<Review> findForUpdate(@Param("reviewId") Long reviewId);
}
