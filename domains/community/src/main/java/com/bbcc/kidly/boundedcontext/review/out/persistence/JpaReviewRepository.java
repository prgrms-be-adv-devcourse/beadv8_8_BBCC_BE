package com.bbcc.kidly.boundedcontext.review.out.persistence;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewModerations;
import com.bbcc.kidly.boundedcontext.review.app.port.Reviews;
import com.bbcc.kidly.boundedcontext.review.app.port.WithdrawnAuthors;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationStatus;
import com.bbcc.kidly.boundedcontext.review.domain.Review;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 리뷰 저장소. 목록은 리뷰 left join 상품 복사본 · 작성 자격(구매 사이즈) 한 쿼리이고, 커서 조건은 (작성 시각, id) 튜플 비교라
 * 인덱스 (product_id | member_id, created_at desc, id desc)의 범위 조건으로 쓰인다.
 */
public interface JpaReviewRepository extends JpaRepository<Review, Long>, Reviews, WithdrawnAuthors,
    ReviewModerations {

    String STATUS = "com.bbcc.kidly.boundedcontext.review.domain.ModerationStatus.";
    String LIST_SELECT = """
        select r.id as id, r.orderItemId as orderItemId, r.productId as productId, p.name as productName,
               p.thumbnailUrl as thumbnailUrl, p.displayStatus as displayStatus, r.rating as rating,
               r.content as content, r.nickname as nickname, r.authorWithdrawnAt as authorWithdrawnAt,
               r.fit as fit, e.size as purchasedSize, r.moderationStatus as moderationStatus,
               r.createdAt as createdAt, r.updatedAt as updatedAt
        from CommunityReview r
        left join CommunityReplicaProduct p on p.productId = r.productId
        left join CommunityReviewEligibility e on e.orderItemId = r.orderItemId
        """;
    String AFTER = " and (r.createdAt, r.id) < (:createdAt, :id)";
    // 공개 상태(검사 대기 · 통과 · 다시 공개). 상품별 인덱스 (product_id, created_at desc, id desc)의 거르기 조건으로 쓰인다
    String PUBLIC_ONLY = " and r.moderationStatus in (" + STATUS + "PENDING, " + STATUS + "PASSED, "
        + STATUS + "RESTORED)";
    String NEWEST_FIRST = " order by r.createdAt desc, r.id desc";
    String ADMIN_SELECT = """
        select r.id as id, r.productId as productId, r.moderationStatus as moderationStatus,
               r.moderationReason as moderationReason, r.moderatedAt as moderatedAt,
               r.moderationAttempts as moderationAttempts, r.content as content, r.createdAt as createdAt
        from CommunityReview r
        """;

    @Override
    @Modifying
    @Query("""
        update CommunityReview r set r.nickname = null, r.authorWithdrawnAt = :withdrawnAt
        where r.memberId = :memberId and r.authorWithdrawnAt is null
        """)
    int markWithdrawn(@Param("memberId") Long memberId, @Param("withdrawnAt") LocalDateTime withdrawnAt);

    @Override
    @Query(LIST_SELECT + " where r.productId = :productId" + PUBLIC_ONLY + NEWEST_FIRST)
    List<ReviewListRow> findByProduct(@Param("productId") Long productId, Limit limit);

    @Override
    @Query(LIST_SELECT + " where r.productId = :productId" + PUBLIC_ONLY + AFTER + NEWEST_FIRST)
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
    @Query("""
        select r.id as id, r.contentVersion as contentVersion, r.moderationAttempts as moderationAttempts,
               r.content as content
        from CommunityReview r
        where r.moderationStatus = com.bbcc.kidly.boundedcontext.review.domain.ModerationStatus.PENDING
          and r.moderationAttempts < :maxAttempts
        order by r.id
        """)
    List<ModerationCandidateRow> findModerationCandidates(@Param("maxAttempts") int maxAttempts, Limit limit);

    @Override
    @Query(ADMIN_SELECT + " where r.moderationStatus = :status order by r.id desc")
    List<AdminReviewRow> findByModerationStatus(@Param("status") ModerationStatus status, Limit limit);

    @Override
    @Query(ADMIN_SELECT + " where r.moderationStatus = :status and r.id < :id order by r.id desc")
    List<AdminReviewRow> findByModerationStatusAfter(@Param("status") ModerationStatus status, @Param("id") Long id,
        Limit limit);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CommunityReview r where r.id = :reviewId")
    Optional<Review> findForUpdate(@Param("reviewId") Long reviewId);
}
