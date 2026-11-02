package com.bbcc.kidly.boundedcontext.review.out.persistence;

import com.bbcc.kidly.boundedcontext.review.app.port.Reviews;
import com.bbcc.kidly.boundedcontext.review.domain.Review;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaReviewRepository extends JpaRepository<Review, Long>, Reviews {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CommunityReview r where r.id = :reviewId")
    Optional<Review> findForUpdate(@Param("reviewId") Long reviewId);
}
