package com.bbcc.kidly.boundedcontext.auth.out.outbox;

import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxMessage;
import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JpaAuthOutboxRepository extends JpaRepository<AuthOutboxMessage, Long>, AuthOutboxRepository {
    @Override
    AuthOutboxMessage save(AuthOutboxMessage message);

    @Override
    @Query(value = "select pg_try_advisory_xact_lock(hashtext('kidly.auth.outbox-relay'))", nativeQuery = true)
    boolean tryLockRelay();

    List<AuthOutboxMessage> findByCreatedAtBeforeOrderByIdAsc(LocalDateTime cutoff, Limit limit);

    @Override
    default List<AuthOutboxMessage> findCreatedBefore(LocalDateTime cutoff, int limit) {
        return findByCreatedAtBeforeOrderByIdAsc(cutoff, Limit.of(limit));
    }
}
