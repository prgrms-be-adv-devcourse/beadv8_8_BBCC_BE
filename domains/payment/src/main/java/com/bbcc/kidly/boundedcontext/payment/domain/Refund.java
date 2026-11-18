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
 * 예치금 환불. 주문상품 취소에 따른 환불은 취소당 하나만 만든다(cancellation_id UK).
 * 주문 전체 취소(order.cancelled)에 따른 환불은 주문상품 취소가 없어 cancellation_id가 null이고, 결제당 한 번만 한다.
 * 취소 사유는 orders에 있으므로 복사하지 않는다.
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

    // 주문상품 취소 ID. 주문 전체 취소로 환불하면 null
    @Column(unique = true)
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

    /**
     * 주문 전체가 취소돼(order.cancelled) 결제 금액을 돌려준다. 주문상품 취소가 없어 cancellationId는 null이다.
     */
    public static Refund completeForOrderCancellation(Long paymentId, Long orderId, List<RefundItem> items,
        LocalDateTime refundedAt) {
        return complete(paymentId, orderId, null, items, refundedAt);
    }

    public static Refund fail(Long paymentId, Long orderId, Long cancellationId, long amount,
        RefundFailureCode failureCode) {
        Refund refund = new Refund(paymentId, orderId, cancellationId, amount, RefundStatus.FAILED);
        refund.failureCode = failureCode;
        return refund;
    }

    /**
     * 실패했던 주문상품 취소 환불을 다시 처리해 환불했다. 취소당 환불 기록은 하나라 새로 만들지 않고 이 기록을 완료로 바꾼다.
     */
    public void completeRetried(List<RefundItem> items, LocalDateTime refundedAt) {
        requireFailed();
        long refundAmount = items.stream().mapToLong(RefundItem::getAmount).sum();
        Amounts.requirePositive(refundAmount);
        this.amount = refundAmount;
        this.status = RefundStatus.COMPLETED;
        this.failureCode = null;
        this.refundedAt = refundedAt;
        items.forEach(this::addItem);
    }

    /**
     * 다시 처리했는데도 환불하지 못했다. 이번 실패 사유로 바꾼다.
     */
    public void failAgain(RefundFailureCode failureCode) {
        requireFailed();
        this.failureCode = failureCode;
    }

    public boolean isFailed() {
        return status == RefundStatus.FAILED;
    }

    private void requireFailed() {
        if (status != RefundStatus.FAILED) {
            throw new IllegalStateException("실패한 환불만 다시 처리할 수 있습니다. refundId=" + id);
        }
    }

    public List<RefundItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    private void addItem(RefundItem item) {
        item.assignTo(this);
        items.add(item);
    }
}
