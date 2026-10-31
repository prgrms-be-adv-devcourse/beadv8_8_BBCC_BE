package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 멈춘 주문 조회 포트를 Spring Data JPA로 구현한다(#445). 목록은 필요한 값만 읽는 투영(지연 로딩 없음) 1번 + 건수 1번.
 * 기준 시각은 앱이 넘긴 값만 쓴다(DB now()는 DB 시간대를 따르므로 쓰지 않음).
 * 배송 컨텍스트의 배송(Delivery · 상태)을 쿼리에서 직접 본다: 컨텍스트 규칙(#617)의 예외 하나로 둔다(컨텍스트별 재구성은 후속).
 */
public interface JpaStuckOrderRepository extends Repository<Order, Long>, StuckOrderRepository {

    String STOCK_CHECK_STALE = """
        from Order o
        where o.status = com.bbcc.kidly.boundedcontext.order.domain.order.Order.Status.PENDING_PAYMENT
          and o.paymentRequestedAt is null and o.createdAt < :before
        """;

    String PAYMENT_EXPIRY_MISSED = """
        from Order o
        where o.status = com.bbcc.kidly.boundedcontext.order.domain.order.Order.Status.PENDING_PAYMENT
          and o.paymentDueAt < :before
          and not exists (
            select r.id
            from PaymentRecovery r
            where r.orderId = o.id
              and r.status = com.bbcc.kidly.boundedcontext.order.domain.recovery.PaymentRecovery.Status.PENDING)
        """;

    String PAID_WITHOUT_DELIVERY = """
        from Order o
        where o.status = com.bbcc.kidly.boundedcontext.order.domain.order.Order.Status.PAID
          and o.paidAt < :before
          and not exists (select d.id from Delivery d where d.orderId = o.id)
        """;

    String RECOVERY_PENDING_STALE = """
        from PaymentRecovery r
        where r.status = com.bbcc.kidly.boundedcontext.order.domain.recovery.PaymentRecovery.Status.PENDING
          and r.createdAt < :before
          and not exists (
            select o.id
            from Order o
            where o.id = r.orderId
              and o.status = com.bbcc.kidly.boundedcontext.order.domain.order.Order.Status.CANCELLED)
        """;

    String SHIPPING_DELAYED = """
        from Delivery d
        where d.status = com.bbcc.kidly.boundedcontext.delivery.domain.Delivery.Status.READY
          and d.createdAt < :before
        """;

    String AUTO_CONFIRM_MISSED = """
        from OrderItem i
        where i.status = com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem.Status.ORDERED
          and i.deliveredAt < :before
          and i.order.status <> com.bbcc.kidly.boundedcontext.order.domain.order.Order.Status.CANCELLED
        """;

    @Override
    @Query(value = "select o.id as orderId, o.id as targetId, o.createdAt as basisAt " + STOCK_CHECK_STALE
        + "order by o.createdAt asc, o.id asc",
        countQuery = "select count(o) " + STOCK_CHECK_STALE)
    Page<StuckRow> findStockCheckStale(LocalDateTime before, Pageable pageable);

    @Override
    @Query("select count(o) " + STOCK_CHECK_STALE)
    long countStockCheckStale(LocalDateTime before);

    @Override
    @Query(value = "select o.id as orderId, o.id as targetId, o.paymentDueAt as basisAt " + PAYMENT_EXPIRY_MISSED
        + "order by o.paymentDueAt asc, o.id asc",
        countQuery = "select count(o) " + PAYMENT_EXPIRY_MISSED)
    Page<StuckRow> findPaymentExpiryMissed(LocalDateTime before, Pageable pageable);

    @Override
    @Query("select count(o) " + PAYMENT_EXPIRY_MISSED)
    long countPaymentExpiryMissed(LocalDateTime before);

    @Override
    @Query(value = "select o.id as orderId, o.id as targetId, o.paidAt as basisAt " + PAID_WITHOUT_DELIVERY
        + "order by o.paidAt asc, o.id asc",
        countQuery = "select count(o) " + PAID_WITHOUT_DELIVERY)
    Page<StuckRow> findPaidWithoutDelivery(LocalDateTime before, Pageable pageable);

    @Override
    @Query("select count(o) " + PAID_WITHOUT_DELIVERY)
    long countPaidWithoutDelivery(LocalDateTime before);

    @Override
    @Query(value = "select r.orderId as orderId, r.id as targetId, r.createdAt as basisAt " + RECOVERY_PENDING_STALE
        + "order by r.createdAt asc, r.id asc",
        countQuery = "select count(r) " + RECOVERY_PENDING_STALE)
    Page<StuckRow> findRecoveryPendingStale(LocalDateTime before, Pageable pageable);

    @Override
    @Query("select count(r) " + RECOVERY_PENDING_STALE)
    long countRecoveryPendingStale(LocalDateTime before);

    @Override
    @Query(value = "select d.orderId as orderId, d.id as targetId, d.createdAt as basisAt " + SHIPPING_DELAYED
        + "order by d.createdAt asc, d.id asc",
        countQuery = "select count(d) " + SHIPPING_DELAYED)
    Page<StuckRow> findShippingDelayed(LocalDateTime before, Pageable pageable);

    @Override
    @Query("select count(d) " + SHIPPING_DELAYED)
    long countShippingDelayed(LocalDateTime before);

    @Override
    @Query(value = "select i.order.id as orderId, i.id as targetId, i.deliveredAt as basisAt " + AUTO_CONFIRM_MISSED
        + "order by i.deliveredAt asc, i.id asc",
        countQuery = "select count(i) " + AUTO_CONFIRM_MISSED)
    Page<StuckRow> findAutoConfirmMissed(LocalDateTime before, Pageable pageable);

    @Override
    @Query("select count(i) " + AUTO_CONFIRM_MISSED)
    long countAutoConfirmMissed(LocalDateTime before);

    @Override
    default Optional<OutboxHeadRow> findOldestUnsentOutbox() {
        return findUnsentOutboxOrderByIdAsc(Limit.of(1)).stream().findFirst();
    }

    // 미전송 부분 인덱스(idx_order_outbox_unsent)를 id 순으로 읽는다
    @Query("""
        select m.id as id, m.eventId as eventId, m.eventType as eventType, m.messageKey as messageKey,
               m.attemptCount as attemptCount, m.lastError as lastError, m.createdAt as createdAt,
               m.updatedAt as updatedAt
        from OrderOutboxMessage m
        where m.sentAt is null
        order by m.id asc
        """)
    List<OutboxHeadRow> findUnsentOutboxOrderByIdAsc(Limit limit);

    @Override
    @Query("select count(m) from OrderOutboxMessage m where m.sentAt is null")
    long countUnsentOutbox();
}
