package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderStatusHistoryRepository;
import com.bbcc.kidly.boundedcontext.order.domain.history.OrderStatusHistory;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 주문 상태 변경 이력 저장소 포트를 Spring Data JPA로 구현한다. 포트에 선언한 메서드만 노출한다.
 */
public interface JpaOrderStatusHistoryRepository
    extends Repository<OrderStatusHistory, Long>, OrderStatusHistoryRepository {

    @Override
    @Query("select h.orderId as orderId, max(h.id) as lastHistoryId from OrderStatusHistory h"
        + " where h.orderId in :orderIds group by h.orderId")
    List<LastHistory> findLastHistoryIds(Collection<Long> orderIds);

    @Override
    @Query("select h.orderId as orderId, max(h.occurredAt) as cancelledAt from OrderStatusHistory h"
        + " where h.orderId in :orderIds"
        + " and h.targetType = com.bbcc.kidly.boundedcontext.order.domain.history.OrderStatusHistory.TargetType.ORDER"
        + " and h.toStatus = 'CANCELLED' group by h.orderId")
    List<CancelledAt> findOrderCancelledAt(Collection<Long> orderIds);
}
