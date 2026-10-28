package com.bbcc.kidly.boundedcontext.payment.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.global.error.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제 금액 중 주문상품 하나에 해당하는 몫. 주문상품을 환불할 때 이 금액을 넘지 않게 한다.
 * 상품명·수량 같은 주문상품 정보는 orders의 order_item 스냅샷에 있으므로 복사하지 않는다.
 */
@Entity
@Table(schema = "payment", name = "payment_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(nullable = false, unique = true)
    private Long orderItemId;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long refundedAmount;

    private PaymentItem(Long orderItemId, long amount) {
        this.orderItemId = orderItemId;
        this.amount = amount;
    }

    public static PaymentItem of(Long orderItemId, long amount) {
        Amounts.requirePositive(amount);
        return new PaymentItem(orderItemId, amount);
    }

    void assignTo(Payment payment) {
        this.payment = payment;
    }

    void refund(long refundAmount) {
        Amounts.requirePositive(refundAmount);
        if (refundedAmount + refundAmount > amount) {
            throw new BusinessException(PaymentErrorCode.REFUND_AMOUNT_EXCEEDED);
        }
        this.refundedAmount += refundAmount;
    }
}
