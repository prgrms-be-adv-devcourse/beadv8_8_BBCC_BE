package com.bbcc.kidly.boundedcontext.childprofile.out.outbox;

import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxMessage;
import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaChildProfileOutboxRepository
    extends JpaRepository<ChildProfileOutboxMessage, Long>, ChildProfileOutboxRepository {

    @Override
    ChildProfileOutboxMessage save(ChildProfileOutboxMessage message);

    boolean existsByEventId(UUID eventId);

    @Override
    @Query(value = "select pg_try_advisory_xact_lock(hashtext('kidly.childprofile.outbox-relay'))", nativeQuery = true)
    boolean tryLockRelay();

    List<ChildProfileOutboxMessage> findBySentAtIsNullOrderByIdAsc(Limit limit);

    @Override
    default List<ChildProfileOutboxMessage> findUnsent(int limit) {
        return findBySentAtIsNullOrderByIdAsc(Limit.of(limit));
    }

    @Override
    @Modifying
    @Query("delete from ChildProfileOutboxMessage m where m.sentAt < :cutoff")
    int deleteSentBefore(@Param("cutoff") LocalDateTime cutoff);
}
