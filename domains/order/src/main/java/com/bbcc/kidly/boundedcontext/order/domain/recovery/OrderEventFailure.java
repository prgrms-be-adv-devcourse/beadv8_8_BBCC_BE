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
 * 받은 이벤트 처리 실패 보관(#449). 재고 차감됨 · 재고 차감 실패 · 결제 실패 처리가 실패한 주문의 받은 이벤트 데이터와
 * 실패 이유를 (주문, 이벤트 종류)당 1건 남긴다. PENDING이면 관리자가 재처리하거나 종료한다(종료는 주문을 바꾸지 않음).
 * 결제 완료 반영 실패(PaymentRecovery)와 달리 돈이 빠지지 않았으므로 결제 마감 만료 대상에서 빼지 않는다.
 */
@Entity
@Table(schema = "orders", name = "order_event_failure", indexes = {
    @Index(name = OrderEventFailure.ORDER_EVENT_UNIQUE, columnList = "order_id, event_type", unique = true),
    @Index(name = "idx_order_event_failure_status", columnList = "status")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderEventFailure extends OrderTimeEntity {

    public static final String ORDER_EVENT_UNIQUE = "uk_order_event_failure_order_event";
    /** 주문이 다른 경로로 취소돼 닫혔다(#598). 실패 코드 칸에 남기는 이유 */
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";

    public enum EventType {
        STOCK_DEDUCTED,
        STOCK_DEDUCTION_FAILED,
        PAYMENT_FAILED
    }

    public enum Status {
        PENDING,
        RECOVERED,
        CANCELLED
    }

    /**
     * 재고 차감 실패가 보낸 주문상품별 살 수 없던 사유(리스너가 거른 뒤의 값, 상품 도메인 값의 이름).
     */
    @Embeddable
    public record StockFailure(
        @Column(nullable = false) Long orderItemId,
        @Column(nullable = false, length = 50) String reason) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventType eventType;

    @Column(nullable = false)
    private Long orderId;

    @ElementCollection
    @CollectionTable(schema = "orders", name = "order_event_failure_item",
        joinColumns = @JoinColumn(name = "order_event_failure_id"))
    @OrderBy("orderItemId")
    private List<StockFailure> stockFailures = new ArrayList<>();

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
     * 이벤트 처리가 처음 실패했을 때 받은 이벤트 데이터와 실패 이유를 보관한다(PENDING · 시도 1).
     *
     * @param stockFailures 재고 차감 실패의 주문상품별 사유. 다른 종류는 빈 목록
     */
    public static OrderEventFailure open(EventType eventType, Long orderId, List<StockFailure> stockFailures,
        String failureCode, LocalDateTime now) {
        OrderEventFailure failure = new OrderEventFailure();
        failure.eventType = eventType;
        failure.orderId = orderId;
        failure.stockFailures = new ArrayList<>(stockFailures);
        failure.status = Status.PENDING;
        failure.failureCode = failureCode;
        failure.attemptCount = 1;
        failure.lastAttemptedAt = now;
        return failure;
    }

    /**
     * 다시 실패했다. 시도 횟수 · 실패 코드 · 시도 시각만 바꾸고 받은 이벤트 데이터는 처음 값을 유지한다.
     * 종료된 기록(RECOVERED · CANCELLED)은 바꾸지 않는다.
     */
    public void recordFailure(String failureCode, LocalDateTime now) {
        if (isClosed()) {
            return;
        }
        this.attemptCount++;
        this.failureCode = failureCode;
        this.lastAttemptedAt = now;
    }

    /**
     * 재처리로 이벤트 처리를 마쳤다. PENDING만 복구할 수 있다(startRetry가 먼저 거른다).
     */
    public void recover(LocalDateTime now) {
        requirePending();
        this.status = Status.RECOVERED;
        this.lastAttemptedAt = now;
    }

    /**
     * 기록을 종료한다(주문은 바꾸지 않음). PENDING만 종료할 수 있다(startRetry · closeByAdmin이 먼저 거른다).
     */
    public void cancel(LocalDateTime now) {
        requirePending();
        this.status = Status.CANCELLED;
        this.lastAttemptedAt = now;
    }

    /**
     * 주문이 다른 경로(구매자 · 만료 · 재고 부족 · 결제 실패 등)로 취소돼 재처리할 것이 없다(#598). 기록만 닫고 이유를 실패 코드에 남긴다.
     * PENDING만(저장소가 PENDING만 읽어 넘긴다).
     */
    public void closeForOrderCancelled(LocalDateTime now) {
        requirePending();
        this.status = Status.CANCELLED;
        this.failureCode = ORDER_CANCELLED;
        this.lastAttemptedAt = now;
    }

    /**
     * 관리자 재처리를 시작할지(#642). PENDING이면 실행하고, 이미 복구됐으면 실행하지 않는다(지금 값 그대로 돌려줌).
     *
     * @throws OrderRuleViolationException 종료됐으면 EVENT_FAILURE_CLOSED
     */
    public boolean startRetry() {
        return switch (status) {
            case PENDING -> true;
            case RECOVERED -> false;
            case CANCELLED -> throw new OrderRuleViolationException(Rule.EVENT_FAILURE_CLOSED);
        };
    }

    /**
     * 재처리를 마쳤다(#642). 주문이 결제대기였으면(이번에 처리함) 복구, 이미 결제대기가 아니었으면(만료 취소 · 다른 이유로 끝남)
     * 처리하지 않았으므로 기록만 종료한다.
     */
    public void finishRetry(boolean orderAwaitingPayment, LocalDateTime now) {
        if (orderAwaitingPayment) {
            recover(now);
        } else {
            cancel(now);
        }
    }

    /**
     * 재처리가 다시 실패했다(#642). 종료된 기록은 그대로 둔다. 그 사이 다른 경로로 주문이 취소됐으면 재처리할 것이 없으므로
     * 닫고(#598), 아니면 시도를 센다. 주문 상태는 종료되지 않은 기록일 때만 읽는다.
     *
     * @param orderCancelled 주문이 취소됐는지(필요할 때만 부른다)
     */
    public void retryFailed(BooleanSupplier orderCancelled, String failureCode, LocalDateTime now) {
        if (isClosed()) {
            return;
        }
        if (orderCancelled.getAsBoolean()) {
            closeForOrderCancelled(now);
        } else {
            recordFailure(failureCode, now);
        }
    }

    /**
     * 관리자가 기록을 종료한다(#642, 주문은 바꾸지 않음). 이미 종료됐으면 그대로 둔다.
     *
     * @return 이번에 종료했으면 true
     * @throws OrderRuleViolationException 이미 복구됐으면 EVENT_FAILURE_CLOSED
     */
    public boolean closeByAdmin(LocalDateTime now) {
        if (status == Status.CANCELLED) {
            return false;
        }
        if (status == Status.RECOVERED) {
            throw new OrderRuleViolationException(Rule.EVENT_FAILURE_CLOSED);
        }
        cancel(now);
        return true;
    }

    public boolean isClosed() {
        return status != Status.PENDING;
    }

    private void requirePending() {
        if (status != Status.PENDING) {
            throw new IllegalStateException("재처리 대기 기록만 종료할 수 있습니다: status=" + status);
        }
    }
}
