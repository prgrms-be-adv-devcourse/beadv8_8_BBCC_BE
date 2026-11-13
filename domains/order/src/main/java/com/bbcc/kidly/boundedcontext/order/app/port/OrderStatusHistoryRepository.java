package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.history.OrderStatusHistory;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 주문 상태 변경 이력 저장소(#442). 구현은 out/persistence (Spring Data가 만든다).
 */
public interface OrderStatusHistoryRepository {

    OrderStatusHistory save(OrderStatusHistory history);

    /**
     * 주문의 이력. 오래된 것부터(같은 시각은 저장 순서).
     */
    List<OrderStatusHistory> findAllByOrderIdOrderByOccurredAtAscIdAsc(Long orderId);

    /**
     * 주문들의 마지막(가장 큰) 이력 ID(#926 진단 중복 판단). 이력이 없는 주문은 빠진다.
     */
    List<LastHistory> findLastHistoryIds(Collection<Long> orderIds);

    /**
     * 주문들이 CANCELLED가 된 시각(#1325, 주문 대상 이력 중 가장 최근). 그 이력이 없는 주문은 빠진다.
     */
    List<CancelledAt> findOrderCancelledAt(Collection<Long> orderIds);

    interface CancelledAt {

        Long getOrderId();

        LocalDateTime getCancelledAt();
    }

    interface LastHistory {

        Long getOrderId();

        Long getLastHistoryId();
    }
}
