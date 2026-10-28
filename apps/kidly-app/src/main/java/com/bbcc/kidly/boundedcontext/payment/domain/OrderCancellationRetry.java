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
 * 처리하다 실패한 주문 취소(order.cancelled). 결제 스케줄러가 다시 처리하고, 성공하면 지운다.
 * 받은 이벤트를 그대로 다시 처리할 수 있게 사유는 취소된 주문 기록(30자)보다 길게 받아 둔다.
 */
@Entity
@Table(schema = "payment", name = "order_cancellation_retry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderCancellationRetry extends BaseTimeEntity {

    private static final int ERROR_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false)
    private Long memberId;

    // 주문이 보낸 취소 사유 이름
    private String reason;

    @Column(nullable = false)
    private int attemptCount;

    @Column(length = ERROR_MAX_LENGTH)
    private String lastError;

    @Column(nullable = false)
    private LocalDateTime lastFailedAt;

    private OrderCancellationRetry(Long orderId, Long memberId, String reason) {
        this.orderId = orderId;
        this.memberId = memberId;
        this.reason = reason;
    }

    public static OrderCancellationRetry failed(Long orderId, Long memberId, String reason, String error,
                                                LocalDateTime failedAt) {
        OrderCancellationRetry retry = new OrderCancellationRetry(orderId, memberId, reason);
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
}
