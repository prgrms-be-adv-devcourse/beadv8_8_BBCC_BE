package com.bbcc.kidly.boundedcontext.payment.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.global.error.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 하나를 예치금으로 결제한 결과. 주문당 하나만 만든다(order_id UK).
 * 주문상품 단위로 환불되며, 환불 누적액에 따라 PARTIALLY_REFUNDED·REFUNDED가 된다.
 */
@Entity
@Table(schema = "payment", name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false)
    private Long walletId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long refundedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PaymentFailureCode failureCode;

    private LocalDateTime paidAt;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL)
    private List<PaymentItem> items = new ArrayList<>();

    private Payment(Long orderId, Long walletId, Long memberId, long amount, PaymentStatus status) {
        this.orderId = orderId;
        this.walletId = walletId;
        this.memberId = memberId;
        this.amount = amount;
        this.status = status;
    }

    /**
     * 지갑에서 차감한 뒤 결제 완료 기록을 만든다. 결제 금액은 주문상품 금액의 합이다.
     */
    public static Payment complete(Long orderId, Long walletId, Long memberId, List<PaymentItem> items,
        LocalDateTime paidAt) {
        long amount = items.stream().mapToLong(PaymentItem::getAmount).sum();
        Amounts.requirePositive(amount);
        Payment payment = new Payment(orderId, walletId, memberId, amount, PaymentStatus.COMPLETED);
        payment.paidAt = paidAt;
        items.forEach(payment::addItem);
        return payment;
    }

    public static Payment fail(Long orderId, Long walletId, Long memberId, long amount,
        PaymentFailureCode failureCode) {
        Payment payment = new Payment(orderId, walletId, memberId, amount, PaymentStatus.FAILED);
        payment.failureCode = failureCode;
        return payment;
    }

    /**
     * 주문상품 하나의 결제 금액 중 일부나 전부를 환불 처리한다.
     *
     * @return 환불한 결제 항목. 환불 항목(refund_item)을 만들 때 쓴다
     */
    public PaymentItem refund(Long orderItemId, long refundAmount) {
        if (status != PaymentStatus.COMPLETED && status != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
        PaymentItem item = items.stream()
            .filter(candidate -> candidate.getOrderItemId().equals(orderItemId))
            .findFirst()
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_ITEM_NOT_FOUND));
        item.refund(refundAmount);
        this.refundedAmount += refundAmount;
        this.status = refundedAmount == amount ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        return item;
    }

    public List<PaymentItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    private void addItem(PaymentItem item) {
        item.assignTo(this);
        items.add(item);
    }
}
