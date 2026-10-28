package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.history.OrderStatusHistory;
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
}
