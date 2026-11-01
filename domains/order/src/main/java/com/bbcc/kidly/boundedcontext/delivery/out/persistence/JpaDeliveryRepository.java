package com.bbcc.kidly.boundedcontext.delivery.out.persistence;

import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 배송 저장소 포트를 Spring Data JPA로 구현한다. 포트에 선언한 메서드만 노출한다.
 * 발송 지연 조회(#617-2)는 멈춘 주문 조회(#445)에서 옮긴 쿼리 그대로다(투영 1번 + 건수 1번, 기준 시각은 앱이 넘긴 값만).
 */
public interface JpaDeliveryRepository extends Repository<Delivery, Long>, DeliveryRepository {

    String SHIPPING_DELAYED = """
        from Delivery d
        where d.status = com.bbcc.kidly.boundedcontext.delivery.domain.Delivery.Status.READY
          and d.createdAt < :before
        """;

    @Override
    @Query(value = "select d.orderId as orderId, d.id as deliveryId, d.createdAt as createdAt " + SHIPPING_DELAYED
        + "order by d.createdAt asc, d.id asc",
        countQuery = "select count(d) " + SHIPPING_DELAYED)
    Page<ShippingDelayedRow> findShippingDelayed(LocalDateTime before, Pageable pageable);

    @Override
    @Query("select count(d) " + SHIPPING_DELAYED)
    long countShippingDelayed(LocalDateTime before);

    @Override
    @Query("""
        select d
        from Delivery d
        where d.status = :status and d.trackingStoppedAt is null and d.trackingNumber is not null and d.id > :afterId
        order by d.id asc
        """)
    List<Delivery> findTrackingTargets(Delivery.Status status, Long afterId, Limit limit);
}
