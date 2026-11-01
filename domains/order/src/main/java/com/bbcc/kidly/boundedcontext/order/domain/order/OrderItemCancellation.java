package com.bbcc.kidly.boundedcontext.order.domain.order;

import com.bbcc.kidly.boundedcontext.order.domain.OrderTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매자 취소 한 건(#655): 한 주문의 한 판매자 몫(배송 한 건)을 발송 전에 취소한 기록. ID가 이벤트(order-item.cancelled)의
 * cancellationId이고, 결제가 환불에 실어 돌려준다. 취소 시각은 만든 시각(createdAt)이다.
 * (주문, 판매자)당 한 건이라 같은 몫을 두 번 취소하면 유일 제약이 막는다(배송 상태 확인이 먼저 막는다).
 * 메모는 판매자가 남긴 글이라 이벤트 · 로그에 싣지 않는다.
 */
@Entity
@Table(schema = "orders", name = "order_item_cancellation", indexes = @Index(
    name = "uk_order_item_cancellation_order_seller", columnList = "order_id, seller_id", unique = true))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItemCancellation extends OrderTimeEntity {

    /**
     * 메모 최대 길이. 요청 DTO의 @Size와 같은 값.
     */
    public static final int MEMO_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderItem.CancelReason reason;

    @Column(length = MEMO_MAX_LENGTH)
    private String memo;

    public static OrderItemCancellation create(Long orderId, Long sellerId, OrderItem.CancelReason reason,
        String memo) {
        OrderItemCancellation cancellation = new OrderItemCancellation();
        cancellation.orderId = orderId;
        cancellation.sellerId = sellerId;
        cancellation.reason = reason;
        cancellation.memo = memo;
        return cancellation;
    }
}
