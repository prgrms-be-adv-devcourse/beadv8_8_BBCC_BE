package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 주문 저장소 포트를 Spring Data JPA로 구현한다. 포트에 선언한 메서드만 노출한다.
 */
public interface JpaOrderRepository extends Repository<Order, Long>, OrderRepository {

    /**
     * 주문상품을 fetch join으로 함께 읽는다. 주문상품 순서는 @OrderBy와 같게 ID 오름차순으로 명시한다.
     * 조인으로 늘어난 주문 행은 Hibernate가 결과에서 하나로 합친다.
     */
    @Override
    @Query("""
        select o
        from Order o
        left join fetch o.items i
        where o.id in :ids
        order by o.id asc, i.id asc
        """)
    List<Order> findAllWithItemsByIdIn(Collection<Long> ids);

    @Override
    @Query("""
        select o.id
        from Order o
        where o.status = :status and o.paymentDueAt <= :now and o.id > :afterId
        order by o.id asc
        """)
    List<Long> findOverduePaymentOrderIds(Order.Status status, LocalDateTime now, Long afterId, Limit limit);

    @Override
    @Query("""
        select i.order.id as orderId, i.id as orderItemId
        from OrderItem i
        where i.status = :status and i.deliveredAt <= :deliveredBefore and i.id > :afterId
        order by i.id asc
        """)
    List<AutoConfirmRow> findAutoConfirmTargets(
        OrderItem.Status status, LocalDateTime deliveredBefore, Long afterId, Limit limit);
}
