package com.bbcc.kidly.boundedcontext.payment.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
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
 * 환불 금액 중 결제 항목 하나에 해당하는 몫.
 */
@Entity
@Table(schema = "payment", name = "refund_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_id")
    private Refund refund;

    @Column(name = "payment_item_id", nullable = false)
    private Long paymentItemId;

    // DB FK(payment_item_id → payment_item)를 만들기 위한 읽기 전용 매핑. 환불이 결제 항목을 바꾸지 않도록 쓰지 않는다
    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_item_id", insertable = false, updatable = false)
    private PaymentItem paymentItem;

    @Column(nullable = false)
    private long amount;

    private RefundItem(Long paymentItemId, long amount) {
        this.paymentItemId = paymentItemId;
        this.amount = amount;
    }

    public static RefundItem of(Long paymentItemId, long amount) {
        Amounts.requirePositive(amount);
        return new RefundItem(paymentItemId, amount);
    }

    void assignTo(Refund refund) {
        this.refund = refund;
    }
}
