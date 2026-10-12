package com.bbcc.kidly.boundedcontext.payment.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
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
 * 주문상품 취소에 따른 예치금 환불. 취소당 하나만 만든다(cancellation_id UK).
 * 취소 사유는 orders의 order_item_cancellation에 있으므로 복사하지 않는다.
 */
@Entity
@Table(schema = "payment", name = "refund")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, unique = true)
    private Long cancellationId;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private RefundFailureCode failureCode;

    private LocalDateTime refundedAt;

    @OneToMany(mappedBy = "refund", cascade = CascadeType.ALL)
    private List<RefundItem> items = new ArrayList<>();

    private Refund(Long paymentId, Long orderId, Long cancellationId, long amount, RefundStatus status) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.cancellationId = cancellationId;
        this.amount = amount;
        this.status = status;
    }

    public static Refund complete(Long paymentId, Long orderId, Long cancellationId, List<RefundItem> items,
        LocalDateTime refundedAt) {
        long amount = items.stream().mapToLong(RefundItem::getAmount).sum();
        Amounts.requirePositive(amount);
        Refund refund = new Refund(paymentId, orderId, cancellationId, amount, RefundStatus.COMPLETED);
        refund.refundedAt = refundedAt;
        items.forEach(refund::addItem);
        return refund;
    }

    public static Refund fail(Long paymentId, Long orderId, Long cancellationId, long amount,
        RefundFailureCode failureCode) {
        Refund refund = new Refund(paymentId, orderId, cancellationId, amount, RefundStatus.FAILED);
        refund.failureCode = failureCode;
        return refund;
    }

    public List<RefundItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    private void addItem(RefundItem item) {
        item.assignTo(this);
        items.add(item);
    }
}
