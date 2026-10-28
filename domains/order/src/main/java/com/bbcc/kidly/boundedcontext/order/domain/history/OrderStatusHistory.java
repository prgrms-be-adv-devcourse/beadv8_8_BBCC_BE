package com.bbcc.kidly.boundedcontext.order.domain.history;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "orders", name = "order_status_history",
    indexes = @Index(name = "idx_order_status_history_order", columnList = "order_id, occurred_at, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderStatusHistory extends BaseTimeEntity {

    public enum TargetType {
        ORDER,
        ORDER_ITEM,
        DELIVERY,
        PAYMENT_RECOVERY
    }

    public enum ActorType {
        BUYER,
        SELLER,
        ADMIN,
        SYSTEM,
        EVENT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TargetType targetType;

    @Column(nullable = false)
    private Long targetId;

    @Column(length = 30)
    private String fromStatus;

    @Column(nullable = false, length = 30)
    private String toStatus;

    @Column(length = 100)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ActorType actorType;

    private Long actorId;

    @Column(nullable = false, length = 100)
    private String source;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    public static OrderStatusHistory of(Long orderId, TargetType targetType, Long targetId, String fromStatus,
        String toStatus, String reason, ActorType actorType, Long actorId, String source, LocalDateTime occurredAt) {
        // 주문 · 대상 ID는 DB가 만든 값이라 DB NOT NULL이 지킨다. 여기서는 코드가 정하는 값만 확인한다
        if (targetType == null || toStatus == null || actorType == null || source == null || occurredAt == null) {
            throw new IllegalArgumentException("주문 상태 이력에 필수 값이 없습니다");
        }
        OrderStatusHistory history = new OrderStatusHistory();
        history.orderId = orderId;
        history.targetType = targetType;
        history.targetId = targetId;
        history.fromStatus = fromStatus;
        history.toStatus = toStatus;
        history.reason = reason;
        history.actorType = actorType;
        history.actorId = actorId;
        history.source = source;
        history.occurredAt = occurredAt;
        return history;
    }
}
