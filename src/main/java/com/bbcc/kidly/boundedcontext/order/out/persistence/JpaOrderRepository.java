package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import org.springframework.data.repository.Repository;

/**
 * 주문 저장소 포트를 Spring Data JPA로 구현한다. 포트에 선언한 메서드만 노출한다.
 */
public interface JpaOrderRepository extends Repository<Order, Long>, OrderRepository {
}
