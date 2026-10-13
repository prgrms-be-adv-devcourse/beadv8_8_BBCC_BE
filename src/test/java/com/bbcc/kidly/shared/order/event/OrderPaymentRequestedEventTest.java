package com.bbcc.kidly.shared.order.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderPaymentRequestedEventTest {

    @Test
    @DisplayName("이벤트 타입은 order.payment-requested, 발행 도메인은 order, 대상은 주문 ID다")
    void metadata() {
        OrderPaymentRequestedEvent event = new OrderPaymentRequestedEvent(UUID.randomUUID(),
            LocalDateTime.of(2026, 10, 14, 9, 0), new OrderPaymentRequestedEvent.Data(7L, 1L, 50_000L,
                List.of(new OrderPaymentRequestedEvent.Item(70L, 50_000L))));

        assertThat(event.eventType()).isEqualTo("order.payment-requested");
        assertThat(event.producer()).isEqualTo("order");
        assertThat(event.aggregateId()).isEqualTo("7");
        assertThat(event.data().items()).hasSize(1);
        assertThat(event.data().items().get(0).orderItemId()).isEqualTo(70L);
        assertThat(event.data().items().get(0).amount()).isEqualTo(50_000L);
    }
}
