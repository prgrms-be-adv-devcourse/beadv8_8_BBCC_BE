package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderItemCancellationRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItemCancellation;
import org.springframework.data.repository.Repository;

/**
 * 판매자 취소 기록 저장소 포트를 Spring Data JPA로 구현한다(#655). 포트에 선언한 메서드만 노출한다.
 */
public interface JpaOrderItemCancellationRepository extends Repository<OrderItemCancellation, Long>,
    OrderItemCancellationRepository {
}
