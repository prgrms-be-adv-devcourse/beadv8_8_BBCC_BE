package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.FailureType;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.Status;
import com.bbcc.kidly.boundedcontext.order.out.messaging.OrderDeadLetter;
import com.bbcc.kidly.boundedcontext.order.out.messaging.ReceivedDeadLetter;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 주문 DLT 메시지 Spring Data JPA 저장소. app은 {@link OrderDeadLetterStore}(포트 구현)로만 쓴다(#642).
 */
public interface JpaOrderDeadLetterRepository extends Repository<OrderDeadLetter, Long> {

    Optional<OrderDeadLetter> findById(Long id);

    /**
     * INSERT … ON CONFLICT(원래 위치) DO UPDATE. 다시 받음은 받은 횟수 · 마지막 받은 시각 · 수정 시각만 바꾸고 version은 올리지 않는다
     * (관리자 처리는 행을 잠그고 최신 값을 읽은 뒤 바꾼다).
     */
    @Modifying
    @Query(nativeQuery = true, value = """
        insert into orders.order_dead_letter (source_topic, source_partition, source_offset, message_key, event_type,
            event_id, payload, producer, occurred_at, aggregate_id, correlation_id, order_id, failure_type,
            exception_class, exception_message, stack_trace_head, first_received_at, last_received_at,
            received_count, status, reprocess_count, version, created_at, updated_at)
        values (:#{#letter.sourceTopic()}, :#{#letter.sourcePartition()}, :#{#letter.sourceOffset()},
            :#{#letter.messageKey()}, :#{#letter.eventType()}, :#{#letter.eventId()}, :#{#letter.payload()},
            :#{#letter.producer()}, :#{#letter.occurredAt()}, :#{#letter.aggregateId()},
            :#{#letter.correlationId()}, :#{#letter.orderId()}, :#{#letter.failureType().name()},
            :#{#letter.exceptionClass()}, :#{#letter.exceptionMessage()}, :#{#letter.stackTraceHead()},
            :#{#letter.receivedAt()}, :#{#letter.receivedAt()}, 1, 'OPEN', 0, 0,
            :#{#letter.receivedAt()}, :#{#letter.receivedAt()})
        on conflict on constraint uk_order_dead_letter_source do update
        set received_count = orders.order_dead_letter.received_count + 1,
            last_received_at = excluded.last_received_at,
            updated_at = excluded.updated_at
        """)
    void saveReceived(@Param("letter") ReceivedDeadLetter letter);

    /**
     * SELECT … FOR UPDATE. 잠금 대기 시간은 따로 두지 않는다(앞 트랜잭션이 끝나면 바로 풀림).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from OrderDeadLetter d where d.id = :id")
    Optional<OrderDeadLetter> findByIdForUpdate(Long id);

    /**
     * SELECT … FOR UPDATE NOWAIT(잠금 대기 시간 0). 이미 잠겨 있으면 기다리지 않고 바로 실패한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("select d from OrderDeadLetter d where d.id = :id")
    Optional<OrderDeadLetter> findByIdForUpdateNoWait(Long id);

    /**
     * 결제 토픽(kidly.payment.events)에서 온 것을 먼저 둔다(#632).
     */
    @Query(value = """
        select d from OrderDeadLetter d
        where d.status = :status
          and (:failureType is null or d.failureType = :failureType)
          and (:eventType is null or d.eventType = :eventType)
        order by case when d.sourceTopic = 'kidly.payment.events' then 0 else 1 end, d.lastReceivedAt desc, d.id desc
        """, countQuery = """
        select count(d) from OrderDeadLetter d
        where d.status = :status
          and (:failureType is null or d.failureType = :failureType)
          and (:eventType is null or d.eventType = :eventType)
        """)
    Page<OrderDeadLetter> findPage(Status status, FailureType failureType,
        String eventType, Pageable pageable);

    @Query("""
        select count(d) from OrderDeadLetter d
        where d.status = :status
          and (:failureType is null or d.failureType = :failureType)
          and (:eventType is null or d.eventType = :eventType)
        """)
    long countPage(Status status, FailureType failureType, String eventType);
}
