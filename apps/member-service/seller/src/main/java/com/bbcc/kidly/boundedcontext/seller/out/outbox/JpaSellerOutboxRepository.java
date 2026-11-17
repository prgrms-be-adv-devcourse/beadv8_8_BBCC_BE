package com.bbcc.kidly.boundedcontext.seller.out.outbox;

import com.bbcc.kidly.boundedcontext.seller.domain.outbox.SellerOutboxMessage;
import com.bbcc.kidly.boundedcontext.seller.domain.outbox.SellerOutboxRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaSellerOutboxRepository
    extends JpaRepository<SellerOutboxMessage, Long>, SellerOutboxRepository {

    // JpaRepository와 SellerOutboxRepository의 save를 둘 다 물려받아, 이 타입으로 save를 부르면 컴파일러가 하나를 고르지 못한다.
    @Override
    SellerOutboxMessage save(SellerOutboxMessage message);

    // PostgreSQL 트랜잭션 advisory lock. 키는 이 릴레이 전용 문자열의 해시라 다른 잠금과 겹치지 않는다.
    @Override
    @Query(value = "select pg_try_advisory_xact_lock(hashtext('kidly.seller.outbox-relay'))", nativeQuery = true)
    boolean tryLockRelay();

    List<SellerOutboxMessage> findBySentAtIsNullAndCreatedAtBeforeOrderByIdAsc(LocalDateTime cutoff, Limit limit);

    @Override
    default List<SellerOutboxMessage> findUnsentCreatedBefore(LocalDateTime cutoff, int limit) {
        return findBySentAtIsNullAndCreatedAtBeforeOrderByIdAsc(cutoff, Limit.of(limit));
    }

    @Override
    @Modifying
    @Query("update SellerOutboxMessage m set m.sentAt = :now, m.attemptCount = m.attemptCount + 1 where m.id in :ids")
    void markSent(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);

    // sent_at이 null인 행은 비교 결과가 참이 아니므로 지우지 않는다
    @Override
    @Modifying
    @Query("delete from SellerOutboxMessage m where m.sentAt < :cutoff")
    int deleteSentBefore(@Param("cutoff") LocalDateTime cutoff);
}
