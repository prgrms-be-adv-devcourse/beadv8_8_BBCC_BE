package com.bbcc.kidly.boundedcontext.settlement.out;

import com.bbcc.kidly.boundedcontext.settlement.domain.outbox.SettlementOutboxMessage;
import com.bbcc.kidly.boundedcontext.settlement.domain.outbox.SettlementOutboxRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface JpaSettlementOutboxRepository
    extends Repository<SettlementOutboxMessage, Long>, SettlementOutboxRepository {

    /**
     * PostgreSQL 트랜잭션 advisory lock. 키는 이 릴레이 전용 문자열의 해시라 다른 잠금과 겹치지 않는다.
     * 세션 잠금(pg_try_advisory_lock)은 쓰지 않는다: 트랜잭션이 끝나면 연결을 풀에 돌려주는 설정이라 같은 연결로 풀 수 없다.
     */
    // 트랜잭션은 붙이지 않는다: 이벤트를 발행한 업무 트랜잭션 안에서만 부른다(SettlementOutboxWriter가 MANDATORY로 확인)
    @Override
    @Modifying
    @Query(value = """
        INSERT INTO settlement.settlement_outbox
            (event_id, event_type, topic, message_key, payload, attempt_count, created_at, updated_at)
        VALUES (:eventId, :eventType, :topic, :messageKey, :payload, 0, :now, :now)
        ON CONFLICT (event_id) DO NOTHING
        """, nativeQuery = true)
    void saveIfAbsent(UUID eventId, String eventType, String topic, String messageKey, String payload,
        LocalDateTime now);

    @Override
    @Query(value = "select pg_try_advisory_xact_lock(hashtext('kidly.settlement.outbox-relay'))", nativeQuery = true)
    boolean tryLockRelay();

    List<SettlementOutboxMessage> findBySentAtIsNullOrderByIdAsc(Limit limit);

    @Override
    default List<SettlementOutboxMessage> findUnsent(int limit) {
        return findBySentAtIsNullOrderByIdAsc(Limit.of(limit));
    }

    // sent_at이 null인 행은 비교 결과가 참이 아니므로 지우지 않는다
    @Override
    @Modifying
    @Query("delete from SettlementOutboxMessage m where m.sentAt < :cutoff")
    int deleteSentBefore(@Param("cutoff") LocalDateTime cutoff);
}
