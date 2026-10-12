package com.bbcc.kidly.shared.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DomainEventTest {

    private static final UUID EVENT_ID = UUID.fromString("6f1c2e4a-0b7d-4c1e-9a55-2d3f8e7b1a90");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 14, 0);

    @Test
    @DisplayName("eventType은 클래스 이름에서 Event를 빼고 첫 단어를 대상, 나머지를 과거형으로 만든다")
    void eventTypeFromClassName() {
        assertThat(new OrderCreatedEvent(EVENT_ID, 1024L, NOW).eventType()).isEqualTo("order.created");
        assertThat(new OrderPaymentRequestedEvent(EVENT_ID, 1024L, NOW).eventType())
            .isEqualTo("order.payment-requested");
    }

    @Test
    @DisplayName("대상이 두 단어 이상인 이벤트는 eventType을 직접 정할 수 있다")
    void eventTypeCanBeOverridden() {
        assertThat(new OrderItemPurchaseConfirmedEvent(EVENT_ID, 5001L, NOW).eventType())
            .isEqualTo("order-item.purchase-confirmed");
    }

    @Test
    @DisplayName("schemaVersion은 1, correlationId는 aggregateId가 기본값이다")
    void defaultValues() {
        OrderCreatedEvent event = new OrderCreatedEvent(EVENT_ID, 1024L, NOW);

        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(event.correlationId()).isEqualTo("1024");
    }

    @Test
    @DisplayName("producer는 shared.{도메인}.event 패키지의 도메인이다")
    void producerFromPackage() {
        assertThat(EventNames.producerOf("com.bbcc.kidly.shared.order.event")).isEqualTo("order");
        assertThat(EventNames.producerOf("com.bbcc.kidly.shared.settlement.event")).isEqualTo("settlement");
    }

    @Test
    @DisplayName("shared.{도메인}.event 밖의 이벤트는 producer를 정할 수 없다")
    void producerOutsideSharedEventPackage() {
        assertThatThrownBy(() -> EventNames.producerOf("com.bbcc.kidly.global.eventpublisher"))
            .isInstanceOf(IllegalStateException.class);
    }

    private record OrderCreatedEvent(UUID eventId, Long orderId, LocalDateTime occurredAt)
        implements DomainEvent {

        @Override
        public String aggregateId() {
            return String.valueOf(orderId);
        }
    }

    private record OrderPaymentRequestedEvent(UUID eventId, Long orderId, LocalDateTime occurredAt)
        implements DomainEvent {

        @Override
        public String aggregateId() {
            return String.valueOf(orderId);
        }
    }

    private record OrderItemPurchaseConfirmedEvent(UUID eventId, Long orderItemId, LocalDateTime occurredAt)
        implements DomainEvent {

        @Override
        public String aggregateId() {
            return String.valueOf(orderItemId);
        }

        @Override
        public String eventType() {
            return "order-item.purchase-confirmed";
        }
    }
}
