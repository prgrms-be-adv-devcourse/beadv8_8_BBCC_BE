package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderStatusHistoryRepository;
import com.bbcc.kidly.boundedcontext.order.domain.history.OrderStatusHistory;
import org.springframework.data.repository.Repository;

/**
 * 주문 상태 변경 이력 저장소 포트를 Spring Data JPA로 구현한다. 포트에 선언한 메서드만 노출한다.
 */
public interface JpaOrderStatusHistoryRepository
    extends Repository<OrderStatusHistory, Long>, OrderStatusHistoryRepository {
}
