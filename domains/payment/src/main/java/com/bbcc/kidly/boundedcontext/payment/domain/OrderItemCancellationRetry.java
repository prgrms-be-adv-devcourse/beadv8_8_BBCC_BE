package com.bbcc.kidly.boundedcontext.payment.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

/**
 * 환불하지 못한 주문상품 취소(order-item.cancelled). 받은 내용을 그대로 남겨 관리자가 다시 처리하거나 닫는다.
 * 취소마다 하나다(cancellation_id UK). 같은 취소가 다시 실패하면 시도 · 마지막 오류만 바꾸고, 환불되면 지운다.
 */
@Entity
@Table(schema = "payment", name = "order_item_cancellation_retry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItemCancellationRetry extends BaseTimeEntity {

    private static final int ERROR_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long cancellationId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long memberId;

    // 주문이 보낸 취소 사유 이름
    private String reason;

    // 관리자 목록 한 쪽(최대 100건)의 품목을 한 번에 읽는다
    @BatchSize(size = 100)
    @ElementCollection
    @CollectionTable(schema = "payment", name = "order_item_cancellation_retry_item",
        joinColumns = @JoinColumn(name = "retry_id"))
    private List<Item> items = new ArrayList<>();

    @Column(nullable = false)
    private int attemptCount;

    // 실패 사유 코드(RefundFailureCode 이름, PAYMENT_NOT_FOUND, MEMBER_MISMATCH)
    @Column(length = ERROR_MAX_LENGTH)
    private String lastError;

    @Column(nullable = false)
    private LocalDateTime lastFailedAt;

    public static OrderItemCancellationRetry failed(Long cancellationId, Long orderId, Long memberId, String reason,
                                                    List<Item> items, String error, LocalDateTime failedAt) {
        OrderItemCancellationRetry retry = new OrderItemCancellationRetry();
        retry.cancellationId = cancellationId;
        retry.orderId = orderId;
        retry.memberId = memberId;
        retry.reason = reason;
        retry.items.addAll(items);
        retry.fail(error, failedAt);
        return retry;
    }

    public void fail(String error, LocalDateTime failedAt) {
        this.attemptCount++;
        this.lastError = error == null || error.length() <= ERROR_MAX_LENGTH
            ? error
            : error.substring(0, ERROR_MAX_LENGTH);
        this.lastFailedAt = failedAt;
    }

    public long getAmount() {
        return items.stream().mapToLong(Item::getAmount).sum();
    }

    public List<Item> getItems() {
        return Collections.unmodifiableList(items);
    }

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Item {

        @Column(nullable = false)
        private Long orderItemId;

        @Column(nullable = false)
        private long amount;

        public Item(Long orderItemId, long amount) {
            this.orderItemId = orderItemId;
            this.amount = amount;
        }
    }
}
