package com.bbcc.kidly.boundedcontext.review.out.persistence;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanups;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewImageCleanup;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 리뷰 사진 정리 대기 저장소. 넣기는 ON CONFLICT DO NOTHING(같은 key는 한 행, 부르는 트랜잭션을 깨지 않음), 끝내기 · 실패 기록은 읽은
 * 시도 횟수가 그대로일 때만 바꾸는 조건부 문장이다. 처리 차례 조회는 (next_attempt_at, id) 인덱스를 쓴다.
 */
public interface JpaReviewImageCleanupRepository extends JpaRepository<ReviewImageCleanup, Long>, ReviewImageCleanups {

    @Override
    @Modifying
    @Query(value = "insert into community.review_image_cleanup (object_key, reason, attempts, next_attempt_at, "
        + "created_at) values (:objectKey, :reason, 0, :nextAttemptAt, :createdAt) "
        + "on conflict (object_key) do nothing", nativeQuery = true)
    int enqueue(@Param("objectKey") String objectKey, @Param("reason") String reason,
        @Param("createdAt") LocalDateTime createdAt, @Param("nextAttemptAt") LocalDateTime nextAttemptAt);

    @Override
    @Modifying
    @Query("delete from CommunityReviewImageCleanup c where c.objectKey in :objectKeys")
    int cancel(@Param("objectKeys") Collection<String> objectKeys);

    @Override
    @Query("select c from CommunityReviewImageCleanup c where c.nextAttemptAt <= :now order by c.nextAttemptAt, c.id")
    List<ReviewImageCleanup> findDue(@Param("now") LocalDateTime now, Limit limit);

    @Override
    @Query("select c.objectKey from CommunityReviewImageCleanup c where c.objectKey in :objectKeys")
    List<String> findQueuedKeys(@Param("objectKeys") Collection<String> objectKeys);

    @Override
    @Modifying
    @Query("delete from CommunityReviewImageCleanup c where c.id = :id and c.attempts = :attempts")
    int finish(@Param("id") Long id, @Param("attempts") int attempts);

    @Override
    @Modifying
    @Query("update CommunityReviewImageCleanup c set c.attempts = c.attempts + 1, c.nextAttemptAt = :nextAttemptAt, "
        + "c.lastFailureType = :failureType where c.id = :id and c.attempts = :attempts")
    int recordFailure(@Param("id") Long id, @Param("attempts") int attempts,
        @Param("nextAttemptAt") LocalDateTime nextAttemptAt, @Param("failureType") String failureType);

    @Override
    @Query("select min(c.createdAt) from CommunityReviewImageCleanup c")
    LocalDateTime findOldestCreatedAt();
}
