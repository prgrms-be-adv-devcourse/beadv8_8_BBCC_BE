package com.bbcc.kidly.boundedcontext.order.domain.delivery;

import com.bbcc.kidly.boundedcontext.order.domain.OrderErrorCode;
import com.bbcc.kidly.global.entity.BaseTimeEntity;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매자별 주문(주문 1건 × 판매자 1명). READY → SHIPPING → DELIVERED 순서로만 바뀐다.
 */
@Entity
@Table(schema = "orders", name = "delivery", indexes = @Index(
    name = "uk_delivery_order_seller",
    columnList = "order_id, seller_id",
    unique = true))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delivery extends BaseTimeEntity {

    /**
     * 배송 상태.
     */
    public enum Status {
        READY,
        SHIPPING,
        DELIVERED
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

    public static Delivery create(Long orderId, Long sellerId) {
        Delivery delivery = new Delivery();
        delivery.orderId = orderId;
        delivery.sellerId = sellerId;
        delivery.status = Status.READY;
        return delivery;
    }

    public void ship(Carrier carrier, String trackingNumber, LocalDateTime now) {
        if (status != Status.READY) {
            throw new BusinessException(OrderErrorCode.INVALID_DELIVERY_STATUS);
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
        if (status == Status.DELIVERED) {
            return;
        }
        if (status != Status.SHIPPING) {
            throw new BusinessException(OrderErrorCode.INVALID_DELIVERY_STATUS);
        }
        this.status = Status.DELIVERED;
        this.deliveredAt = now;
    }
}
