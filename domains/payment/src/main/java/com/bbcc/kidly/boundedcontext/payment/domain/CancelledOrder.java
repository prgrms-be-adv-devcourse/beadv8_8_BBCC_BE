package com.bbcc.kidly.boundedcontext.payment.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제하기 전에 취소된 주문. 그 뒤에 온 결제 요청(order.payment-requested)은 차감하지 않는다(기획서 2.1 ④).
 */
@Entity
@Table(schema = "payment", name = "cancelled_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CancelledOrder extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false)
    private Long memberId;

    // 주문이 보낸 취소 사유 이름 (PAYMENT_FAILED · PAYMENT_EXPIRED · OUT_OF_STOCK)
    @Column(length = 30)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime cancelledAt;

    private CancelledOrder(Long orderId, Long memberId, String reason, LocalDateTime cancelledAt) {
        this.orderId = orderId;
        this.memberId = memberId;
        this.reason = reason;
        this.cancelledAt = cancelledAt;
    }

    public static CancelledOrder of(Long orderId, Long memberId, String reason, LocalDateTime cancelledAt) {
        return new CancelledOrder(orderId, memberId, reason, cancelledAt);
    }
}
