package com.bbcc.kidly.boundedcontext.order.domain.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderStatusHistoryTest {

    private static final LocalDateTime NOW = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

    @Test
    @DisplayName("상태 변경 한 줄: 주문 · 대상 · 이전 · 다음 · 사유 · 누가 · 누가 id · 어디서 · 시각을 그대로 담는다")
    void recordsAllFields() {
        OrderStatusHistory history = OrderStatusHistory.of(12L, OrderStatusHistory.TargetType.ORDER, 12L,
            "PENDING_PAYMENT", "CANCELLED", "BUYER_CANCELLED", OrderStatusHistory.ActorType.BUYER, 5L,
            "POST /api/v1/orders/{orderId}/cancel", NOW);

        assertThat(history.getOrderId()).isEqualTo(12L);
        assertThat(history.getTargetType()).isEqualTo(OrderStatusHistory.TargetType.ORDER);
        assertThat(history.getTargetId()).isEqualTo(12L);
        assertThat(history.getFromStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(history.getToStatus()).isEqualTo("CANCELLED");
        assertThat(history.getReason()).isEqualTo("BUYER_CANCELLED");
        assertThat(history.getActorType()).isEqualTo(OrderStatusHistory.ActorType.BUYER);
        assertThat(history.getActorId()).isEqualTo(5L);
        assertThat(history.getSource()).isEqualTo("POST /api/v1/orders/{orderId}/cancel");
        assertThat(history.getOccurredAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("생성(이전 상태 없음) · 사유 없음 · 누가 id 없음은 null로 담는다")
    void nullableFields() {
        OrderStatusHistory history = OrderStatusHistory.of(12L, OrderStatusHistory.TargetType.DELIVERY, 31L,
            null, "READY", null, OrderStatusHistory.ActorType.EVENT, null, "payment.completed", NOW);

        assertThat(history.getFromStatus()).isNull();
        assertThat(history.getReason()).isNull();
        assertThat(history.getActorId()).isNull();
    }

    @Test
    @DisplayName("대상 종류 · 다음 상태 · 누가 · 어디서 · 시각이 없으면 저장 전에 막는다(불변식, ID는 DB NOT NULL)")
    void requiredFields() {
        assertThatThrownBy(() -> OrderStatusHistory.of(1L, null, 1L, null,
            "PAID", null, OrderStatusHistory.ActorType.EVENT, null, "payment.completed", NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OrderStatusHistory.of(1L, OrderStatusHistory.TargetType.ORDER, 1L, null,
            null, null, OrderStatusHistory.ActorType.EVENT, null, "payment.completed", NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OrderStatusHistory.of(1L, OrderStatusHistory.TargetType.ORDER, 1L, null,
            "PAID", null, null, null, "payment.completed", NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OrderStatusHistory.of(1L, OrderStatusHistory.TargetType.ORDER, 1L, null,
            "PAID", null, OrderStatusHistory.ActorType.EVENT, null, null, NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
