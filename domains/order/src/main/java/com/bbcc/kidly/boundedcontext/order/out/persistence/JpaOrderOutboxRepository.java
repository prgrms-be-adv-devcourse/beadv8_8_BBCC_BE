package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.order.domain.outbox.OrderOutboxMessage;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 주문 이벤트 메시지 저장소 포트를 Spring Data JPA로 구현한다. 포트에 선언한 메서드만 노출한다.
 */
public interface JpaOrderOutboxRepository extends Repository<OrderOutboxMessage, Long>, OrderOutboxRepository {

    /**
     * PostgreSQL 트랜잭션 advisory lock. 키는 이 릴레이 전용 문자열의 해시라 다른 잠금과 겹치지 않는다.
     * 세션 잠금(pg_try_advisory_lock)은 쓰지 않는다: 트랜잭션이 끝나면 연결을 풀에 돌려주는 설정이라 같은 연결로 풀 수 없다.
     */
    @Override
    @Query(value = "select pg_try_advisory_xact_lock(hashtext('kidly.order.outbox-relay'))", nativeQuery = true)
    boolean tryLockRelay();

    @Override
    @Modifying
    @Query("delete from OrderOutboxMessage m where m.sentAt < :cutoff")
    int deleteSentBefore(LocalDateTime cutoff);
}
