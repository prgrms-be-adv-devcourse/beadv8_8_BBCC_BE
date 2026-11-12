package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 주문 저장소 포트를 Spring Data JPA로 구현한다. 포트에 선언한 메서드만 노출한다.
 */
public interface JpaOrderRepository extends Repository<Order, Long>, OrderRepository {

    // 주문상품의 판매자 ID(order_item.seller_id) · 상태 인덱스(idx_order_item_seller_status, #1120)로 찾고 주문 상태로 거른다
    @Override
    @Query("select count(i) from Order o join o.items i where i.product.sellerId = :sellerId "
        + "and i.status = :itemStatus and o.status <> :excludedOrderStatus")
    long countSellerItems(Long sellerId, OrderItem.Status itemStatus, Order.Status excludedOrderStatus);

    @Override
    @Query("""
        select distinct o.id from Order o join o.items i
        where i.product.sellerId = :sellerId and i.status = :itemStatus and o.status in :statuses and o.id > :afterId
        order by o.id asc
        """)
    List<Long> findOrderIdsWithSellerItems(Long sellerId, OrderItem.Status itemStatus,
        Collection<Order.Status> statuses, Long afterId, Limit limit);

    /**
     * SELECT … FOR UPDATE. 잠금 대기 시간은 따로 두지 않는다(앞 트랜잭션이 끝나면 바로 풀림).
     */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(Long id);

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

    /**
     * 판매 주문 조회(#997): 엔티티를 읽지 않고 필요한 칸만 고른다(주문상품의 자녀 칸 child_*는 select하지 않음). 쿼리 한 번.
     */
    @Override
    @Query("""
        select o.id as orderId, o.memberId as memberId, o.createdAt as orderedAt,
               o.shippingAddress.recipientName as recipientName, o.shippingAddress.phone as phone,
               o.shippingAddress.zipCode as zipCode, o.shippingAddress.address as address,
               i.id as orderItemId, i.product.productId as productId, i.product.optionId as optionId,
               i.product.sellerId as sellerId, i.product.productName as productName,
               i.product.optionName as optionName, i.product.size as size, i.product.unitPrice as unitPrice,
               i.quantity as quantity, i.amount as amount, i.status as status, i.deliveredAt as deliveredAt,
               i.confirmedAt as confirmedAt, i.confirmType as confirmType, i.unavailableReason as unavailableReason
        from Order o
        join o.items i
        where o.id in :orderIds
        order by o.id asc, i.id asc
        """)
    List<SellerOrderItemRow> findSellerOrderItemsByOrderIdIn(Collection<Long> orderIds);

    /**
     * 결제 완료 반영 실패로 재처리를 기다리는 주문(기록 PENDING)은 빼서 만료 취소 · 환불하지 않는다(#353).
     * 재처리 불가 · 복구됨 · 취소로 종료 기록은 그대로 대상이다. 기록은 주문당 1건(order_id 유니크 인덱스가 받침).
     */
    @Override
    @Query("""
        select o
        from Order o
        left join fetch o.items i
        where o.id = :id and o.memberId = :memberId
        order by i.id asc
        """)
    Optional<Order> findWithItemsByIdAndMemberId(Long id, Long memberId);

    @Override
    @Query("""
        select o.id
        from Order o
        where o.status = :status and o.paymentDueAt <= :now and o.id > :afterId
          and not exists (
            select r.id
            from PaymentRecovery r
            where r.orderId = o.id
              and r.status = com.bbcc.kidly.boundedcontext.order.domain.recovery.PaymentRecovery.Status.PENDING)
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

    // 자녀 칸 · 가격 · 배송지는 고르지 않는다(projection, #1000). 결제된 적 있는 주문 = paidAt 있음(주문의 기존 판단)
    @Override
    @Query(value = """
        select i.id as orderItemId, o.id as orderId, o.createdAt as orderedAt, i.product.productId as productId,
            i.product.sellerId as sellerId, i.product.productName as productName,
            i.product.optionName as optionName, i.quantity as quantity, i.status as itemStatus,
            o.status as orderStatus, i.deliveredAt as deliveredAt
        from OrderItem i join i.order o
        where i.child.childId = :childId and o.paidAt is not null
        order by o.createdAt desc, i.id desc
        """, countQuery = """
        select count(i) from OrderItem i join i.order o
        where i.child.childId = :childId and o.paidAt is not null
        """)
    Page<ChildPurchaseRow> findChildPurchases(Long childId, Pageable pageable);
}
