package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventFailureRepository;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.OrderEventFailure;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.Repository;

/**
 * 받은 이벤트 처리 실패 기록 저장소 포트를 Spring Data JPA로 구현한다. 포트에 선언한 메서드만 노출한다.
 */
public interface JpaOrderEventFailureRepository extends Repository<OrderEventFailure, Long>,
    OrderEventFailureRepository {

    /**
     * SELECT … FOR UPDATE. 잠금 대기 시간은 따로 두지 않는다(앞 트랜잭션이 끝나면 바로 풀림).
     */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from OrderEventFailure f where f.id = :id")
    Optional<OrderEventFailure> findByIdForUpdate(Long id);

    /**
     * SELECT … FOR UPDATE NOWAIT(잠금 대기 시간 0). 이미 잠겨 있으면 기다리지 않고 바로 실패한다.
     */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("select f from OrderEventFailure f where f.orderId = :orderId and f.eventType = :eventType")
    Optional<OrderEventFailure> findByOrderIdAndEventTypeForUpdateNoWait(Long orderId,
        OrderEventFailure.EventType eventType);

    /**
     * SELECT … FOR UPDATE SKIP LOCKED(잠금 대기 시간 -2 = 건너뜀). 다른 트랜잭션이 잡고 있는 행은 결과에서 빠진다.
     */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select f from OrderEventFailure f where f.orderId = :orderId and f.status = :status order by f.id")
    List<OrderEventFailure> findByOrderIdAndStatusForUpdateSkipLocked(Long orderId, OrderEventFailure.Status status);
}
