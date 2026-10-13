package com.bbcc.kidly.shared.order.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderCreatedEventTest {

    @Test
    @DisplayName("이벤트 타입은 order.created, 발행 도메인은 order, 대상은 주문 ID다")
    void metadata() {
        OrderCreatedEvent event = new OrderCreatedEvent(UUID.randomUUID(), LocalDateTime.of(2026, 10, 14, 9, 0),
            new OrderCreatedEvent.Data(7L, 1L, List.of(new OrderCreatedEvent.Item(70L, 3L, 2))));

        assertThat(event.eventType()).isEqualTo("order.created");
        assertThat(event.producer()).isEqualTo("order");
        assertThat(event.aggregateId()).isEqualTo("7");
    }
}
