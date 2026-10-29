package com.bbcc.kidly.boundedcontext.order.domain.delivery;

import com.bbcc.kidly.boundedcontext.order.domain.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.domain.OrderTimeEntity;
import com.bbcc.kidly.global.error.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매자별 주문(주문 1건 × 판매자 1명). READY → SHIPPING → DELIVERED 순서로만 바뀐다.
 * 구매자가 발송 전에 주문을 취소하면 READY → CANCELLED(#354). 발송과 취소가 동시에 오면 @Version으로 한쪽만 성공한다.
 */
@Entity
@Table(schema = "orders", name = "delivery", indexes = {
    @Index(name = "uk_delivery_order_seller", columnList = "order_id, seller_id", unique = true),
    @Index(name = "idx_delivery_seller_id", columnList = "seller_id")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delivery extends OrderTimeEntity {

    /**
     * 배송 상태.
     */
    public enum Status {
        READY,
        SHIPPING,
        DELIVERED,
        CANCELLED
    }

    /**
     * 택배사. DUMMY는 테스트용. Delivery Tracker 택배사 ID 연결은 연동 때 정한다.
     */
    public enum Carrier {
        CJ,
        EPOST,
        HANJIN,
        LOTTE,
        LOGEN,
        DUMMY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Carrier carrier;

    @Column(length = 50)
    private String trackingNumber;

    private LocalDateTime shippedAt;

    private LocalDateTime deliveredAt;

    @Version
    private Long version;

    public static Delivery create(Long orderId, Long sellerId) {
        Delivery delivery = new Delivery();
        delivery.orderId = orderId;
        delivery.sellerId = sellerId;
        delivery.status = Status.READY;
        return delivery;
    }

    public void ship(Carrier carrier, String trackingNumber, LocalDateTime now) {
        rejectIfCancelled();
        if (status != Status.READY) {
            throw new BusinessException(OrderErrorCode.ALREADY_SHIPPED);
        }
        this.status = Status.SHIPPING;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.shippedAt = now;
    }

    /**
     * 배송 완료. 이미 완료면 그대로 둔다(두 번 눌러도 처음 시각 유지). 발송 전이면 409.
     */
    public void complete(LocalDateTime now) {
        rejectIfCancelled();
        if (status == Status.DELIVERED) {
            return;
        }
        if (status != Status.SHIPPING) {
            throw new BusinessException(OrderErrorCode.NOT_SHIPPED);
        }
        this.status = Status.DELIVERED;
        this.deliveredAt = now;
    }

    /**
     * 구매자가 주문을 취소했다(#354). 발송 전(READY)만 취소할 수 있고, 이미 취소면 그대로 둔다.
     * 발송 · 배송 완료한 배송은 서비스가 먼저 거르므로 여기 오면 불변식 위반이다.
     */
    public void cancel() {
        if (status == Status.CANCELLED) {
            return;
        }
        if (status != Status.READY) {
            throw new IllegalStateException("발송 전 배송만 취소할 수 있습니다: deliveryId=" + id + " status=" + status);
        }
        this.status = Status.CANCELLED;
    }

    private void rejectIfCancelled() {
        if (status == Status.CANCELLED) {
            throw new BusinessException(OrderErrorCode.DELIVERY_CANCELLED);
        }
    }
}
