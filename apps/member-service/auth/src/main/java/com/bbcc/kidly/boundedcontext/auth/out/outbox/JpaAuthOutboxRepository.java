package com.bbcc.kidly.boundedcontext.auth.out.outbox;

import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxMessage;
import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAuthOutboxRepository extends JpaRepository<AuthOutboxMessage, Long>, AuthOutboxRepository {
    @Override
    @Query(value = "select pg_try_advisory_xact_lock(hashtext('kidly.auth.outbox-relay'))", nativeQuery = true)
    boolean tryLockRelay();

    List<AuthOutboxMessage> findBySentAtIsNullAndCreatedAtBeforeOrderByIdAsc(LocalDateTime cutoff, Limit limit);

    @Override
    default List<AuthOutboxMessage> findUnsentCreatedBefore(LocalDateTime cutoff, int limit) {
        return findBySentAtIsNullAndCreatedAtBeforeOrderByIdAsc(cutoff, Limit.of(limit));
    }

    @Override
    @Modifying
    @Query("update AuthOutboxMessage m set m.sentAt = :now, m.attemptCount = m.attemptCount + 1 where m.id in :ids")
    void markSent(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);

    // sent_at이 null인 행은 비교 결과가 참이 아니므로 지우지 않는다
    @Override
    @Modifying
    @Query("delete from AuthOutboxMessage m where m.sentAt < :cutoff")
    int deleteSentBefore(@Param("cutoff") LocalDateTime cutoff);
}
