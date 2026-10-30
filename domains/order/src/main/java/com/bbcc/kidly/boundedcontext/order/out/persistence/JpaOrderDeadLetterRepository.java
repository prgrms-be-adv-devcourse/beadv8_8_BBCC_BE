package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository;
import com.bbcc.kidly.boundedcontext.order.domain.deadletter.OrderDeadLetter;
import com.bbcc.kidly.boundedcontext.order.domain.deadletter.ReceivedDeadLetter;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 주문 DLT 메시지 저장소 포트를 Spring Data JPA로 구현한다. 포트에 선언한 메서드만 노출한다.
 */
public interface JpaOrderDeadLetterRepository extends Repository<OrderDeadLetter, Long>, OrderDeadLetterRepository {

    /**
     * INSERT … ON CONFLICT(원래 위치) DO UPDATE. 다시 받음은 받은 횟수 · 마지막 받은 시각 · 수정 시각만 바꾸고 version은 올리지 않는다
     * (관리자 처리는 행을 잠그고 최신 값을 읽은 뒤 바꾼다).
     */
    @Override
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
}
