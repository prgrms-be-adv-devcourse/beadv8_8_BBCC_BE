package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository.OutboxStatus;
import com.bbcc.kidly.boundedcontext.order.out.outbox.OrderOutboxMessage;
import jakarta.persistence.LockModeType;
import com.bbcc.kidly.boundedcontext.order.out.outbox.OrderOutboxStore;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 아웃박스 메시지 Spring Data JPA 저장소. app은 {@link OrderOutboxStore}(포트 구현)로만 쓰고, 저장은 아웃박스 쓰기(out)가 한다.
 */
public interface JpaOrderOutboxRepository extends Repository<OrderOutboxMessage, Long> {

    OrderOutboxMessage save(OrderOutboxMessage message);

    Optional<OrderOutboxMessage> findById(Long id);

    List<OrderOutboxMessage> findBySentAtIsNullOrderByIdAsc(Limit limit);

    @Query("""
        select m from OrderOutboxMessage m
        where m.sentAt is null and m.quarantinedAt is null
          and not exists (
            select q.id from OrderOutboxMessage q
            where q.sentAt is null and q.quarantinedAt is not null
              and q.topic = m.topic and q.messageKey = m.messageKey and q.id < m.id)
        order by m.id asc
        """)
    List<OrderOutboxMessage> findRelayCandidates(Limit limit);

    String STATUS = """
        select m.id as id, m.eventId as eventId, m.eventType as eventType, m.topic as topic,
               m.messageKey as messageKey, m.attemptCount as attemptCount, m.lastError as lastError,
               m.createdAt as createdAt, m.quarantinedAt as quarantinedAt, m.sentAt as sentAt,
               m.lastRetriedAt as lastRetriedAt, m.lastRetriedBy as lastRetriedBy, m.retryCount as retryCount
        from OrderOutboxMessage m
        """;

    @Query(value = STATUS + "where m.quarantinedAt is not null order by m.id asc",
        countQuery = "select count(m) from OrderOutboxMessage m where m.quarantinedAt is not null")
    Page<OutboxStatus> findQuarantined(Pageable pageable);

    long countByQuarantinedAtIsNotNull();

    @Query(STATUS + "where m.id = :id")
    Optional<OutboxStatus> findStatusById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from OrderOutboxMessage m where m.id = :id")
    Optional<OrderOutboxMessage> findByIdForUpdate(Long id);

    @Modifying
    @Query("delete from OrderOutboxMessage m where m.sentAt < :cutoff")
    int deleteSentBefore(LocalDateTime cutoff);
}
