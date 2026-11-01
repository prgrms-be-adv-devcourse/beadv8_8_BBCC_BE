package com.bbcc.kidly.boundedcontext.order.domain.recovery;

import com.bbcc.kidly.boundedcontext.order.domain.OrderRuleViolationException;
import com.bbcc.kidly.boundedcontext.order.domain.OrderRuleViolationException.Rule;
import com.bbcc.kidly.boundedcontext.order.domain.OrderTimeEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제 완료 반영 실패 보관(#353). 예치금은 빠졌는데 결제완료 반영(PAID · 배송 생성 · 장바구니 정리)이 실패한 주문의
 * 받은 결제 완료 내용과 실패 이유를 주문당 1건 남긴다. PENDING이면 관리자가 재처리하거나 취소로 끝낸다.
 * PENDING인 주문은 결제 마감 만료 대상에서 빠진다(NOT_RETRYABLE은 만료로 취소 · 환불된다).
 */
@Entity
@Table(schema = "orders", name = "payment_recovery", indexes = {
    @Index(name = PaymentRecovery.ORDER_UNIQUE, columnList = "order_id", unique = true),
    @Index(name = "idx_payment_recovery_status", columnList = "status")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentRecovery extends OrderTimeEntity {

    public static final String ORDER_UNIQUE = "uk_payment_recovery_order";

    public enum Status {
        PENDING,
        RECOVERED,
        CANCELLED,
        NOT_RETRYABLE
    }

    @Embeddable
    public record PaidItem(
        Long orderItemId,
        @Column(nullable = false) long amount) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    private Long paymentId;

    private Long memberId;

    @Column(nullable = false)
    private long amount;

    @ElementCollection
    @CollectionTable(schema = "orders", name = "payment_recovery_item",
        joinColumns = @JoinColumn(name = "payment_recovery_id"))
    @OrderBy("orderItemId")
    private List<PaidItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false, length = 100)
    private String failureCode;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private LocalDateTime lastAttemptedAt;

    @Version
    private Long version;

    /**
     * 결제 완료 반영이 처음 실패했을 때 받은 결제 완료 내용과 실패 이유를 보관한다(시도 1).
     *
     * @param retryable 다시 실행하면 될 수 있는 실패인지. 결제 내용 불일치는 false(NOT_RETRYABLE)
     */
    public static PaymentRecovery open(Long orderId, Long paymentId, Long memberId, long amount, List<PaidItem> items,
        String failureCode, boolean retryable, LocalDateTime now) {
        PaymentRecovery recovery = new PaymentRecovery();
        recovery.orderId = orderId;
        recovery.paymentId = paymentId;
        recovery.memberId = memberId;
        recovery.amount = amount;
        recovery.items = new ArrayList<>(items);
        recovery.status = retryable ? Status.PENDING : Status.NOT_RETRYABLE;
        recovery.failureCode = failureCode;
        recovery.attemptCount = 1;
        recovery.lastAttemptedAt = now;
        return recovery;
    }

    /**
     * 다시 실패했다. 시도 횟수 · 실패 이유 · 시도 시각만 바꾸고 결제 내용은 처음 값을 유지한다.
     * 한 번 NOT_RETRYABLE이면 그대로 둔다(결제 내용이 이미 맞지 않음). 종료된 기록(RECOVERED · CANCELLED)은 바꾸지 않는다.
     */
    public void recordFailure(String failureCode, boolean retryable, LocalDateTime now) {
        if (isClosed()) {
            return;
        }
        this.attemptCount++;
        this.failureCode = failureCode;
        this.lastAttemptedAt = now;
        if (!retryable) {
            this.status = Status.NOT_RETRYABLE;
        }
    }

    /**
     * 재처리로 결제완료 반영을 마쳤다. PENDING만 복구할 수 있다(startRetry · finishRetry가 먼저 거른다).
     */
    public void recover(LocalDateTime now) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("재처리 대기 기록만 복구할 수 있습니다: status=" + status);
        }
        this.status = Status.RECOVERED;
        this.lastAttemptedAt = now;
    }

    /**
     * 주문을 취소로 끝냈다. 종료되지 않은 기록(PENDING · NOT_RETRYABLE)만 취소로 종료할 수 있다(startAdminCancel · startRetry가 먼저 거른다).
     */
    public void cancel(LocalDateTime now) {
        if (isClosed()) {
            throw new IllegalStateException("종료된 기록은 취소로 종료할 수 없습니다: status=" + status);
        }
        this.status = Status.CANCELLED;
        this.lastAttemptedAt = now;
    }

    /**
     * 주문이 다른 경로로 취소돼 결제완료로 반영할 수 없다(#598). 기록만 닫고 이유를 실패 코드에 남긴다. 빠진 돈은 주문 취소 이벤트를
     * 받은 결제 쪽이 환불한다. PENDING만(저장소가 PENDING만 읽어 넘긴다, NOT_RETRYABLE은 그대로).
     */
    public void closeForOrderCancelled(LocalDateTime now) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("재처리 대기 기록만 닫을 수 있습니다: status=" + status);
        }
        this.status = Status.CANCELLED;
        this.failureCode = OrderEventFailure.ORDER_CANCELLED;
        this.lastAttemptedAt = now;
    }

    /**
     * 관리자 재처리를 시작할지(#642). PENDING이면 실행하고, 이미 복구됐으면 실행하지 않는다(지금 값 그대로 돌려줌).
     *
     * @throws OrderRuleViolationException 취소로 종료됐으면 RECOVERY_CLOSED, 재처리할 수 없는 기록이면 RECOVERY_NOT_RETRYABLE
     */
    public boolean startRetry() {
        return switch (status) {
            case PENDING -> true;
            case RECOVERED -> false;
            case CANCELLED -> throw new OrderRuleViolationException(Rule.RECOVERY_CLOSED);
            case NOT_RETRYABLE -> throw new OrderRuleViolationException(Rule.RECOVERY_NOT_RETRYABLE);
        };
    }

    /**
     * 재처리를 마쳤다(#642). 그 사이 주문이 취소됐으면(기록 저장과 만료가 경합) 결제완료로 되돌리지 않고 취소로 끝내고, 아니면 복구한다.
     */
    public void finishRetry(boolean orderCancelled, LocalDateTime now) {
        if (orderCancelled) {
            cancel(now);
        } else {
            recover(now);
        }
    }

    /**
     * 재처리가 다시 실패했다(#642). 종료된 기록은 그대로 둔다. PENDING인데 그 사이 다른 경로로 주문이 취소됐으면 반영할 것이 없으므로
     * 닫고(#598), 아니면 시도를 센다. 주문 상태는 PENDING일 때만 읽는다.
     *
     * @param orderCancelled 주문이 취소됐는지(필요할 때만 부른다)
     */
    public void retryFailed(BooleanSupplier orderCancelled, String failureCode, boolean retryable, LocalDateTime now) {
        if (isClosed()) {
            return;
        }
        if (status == Status.PENDING && orderCancelled.getAsBoolean()) {
            closeForOrderCancelled(now);
        } else {
            recordFailure(failureCode, retryable, now);
        }
    }

    /**
     * 관리자 취소를 시작할지(#642). 이미 취소됐으면 하지 않는다(지금 값 그대로 돌려줌).
     *
     * @throws OrderRuleViolationException 이미 복구됐으면 RECOVERY_CLOSED
     */
    public boolean startAdminCancel() {
        if (status == Status.CANCELLED) {
            return false;
        }
        if (status == Status.RECOVERED) {
            throw new OrderRuleViolationException(Rule.RECOVERY_CLOSED);
        }
        return true;
    }

    /**
     * 관리자가 기록을 취소로 끝낸다(#642). 이미 결제가 반영된 주문의 기록은 취소로 끝낼 수 없다.
     *
     * @throws OrderRuleViolationException 주문이 결제완료면 RECOVERY_ORDER_PAID
     */
    public void cancelByAdmin(boolean orderPaid, LocalDateTime now) {
        if (orderPaid) {
            throw new OrderRuleViolationException(Rule.RECOVERY_ORDER_PAID);
        }
        cancel(now);
    }

    public boolean isClosed() {
        return status == Status.RECOVERED || status == Status.CANCELLED;
    }
}
