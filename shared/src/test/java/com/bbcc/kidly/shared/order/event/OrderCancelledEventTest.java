package com.bbcc.kidly.shared.order.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderCancelledEventTest {

    @Test
    @DisplayName("이벤트 타입은 order.cancelled, 발행 도메인은 order, 대상은 주문 ID다")
    void metadata() {
        OrderCancelledEvent event = new OrderCancelledEvent(UUID.randomUUID(), LocalDateTime.of(2026, 10, 15, 9, 0),
            new OrderCancelledEvent.Data(7L, 1L, "PAYMENT_FAILED", List.of(new OrderCancelledEvent.Item(70L, 3L, 2))));

        assertThat(event.eventType()).isEqualTo("order.cancelled");
        assertThat(event.producer()).isEqualTo("order");
        assertThat(event.aggregateId()).isEqualTo("7");
        assertThat(event.data().reason()).isEqualTo("PAYMENT_FAILED");
        assertThat(event.data().items().get(0).optionId()).isEqualTo(3L);
    }
}
