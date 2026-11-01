package com.bbcc.kidly.boundedcontext.order.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.shared.order.event.CartItemAddedEvent;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OrderEventEnvelopeTest {

    private static final UUID EVENT_ID = UUID.fromString("6f1c2e4a-0b7d-4c1e-9a55-2d3f8e7b1a90");
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 29, 17, 30, 0, 123_456_000);

    @Test
    @DisplayName("공통 칸 7개는 이벤트 값(기본값 포함) 그대로, data는 이벤트의 data")
    void fillsCommonFieldsFromEvent() {
        OrderCreatedEvent event = orderCreated();

        OrderEventEnvelope envelope = OrderEventEnvelope.from(event);

        assertThat(envelope).isEqualTo(new OrderEventEnvelope(EVENT_ID, "order.created", 1, OCCURRED_AT, "order",
            "1024", "1024", event.data()));
    }

    @Test
    @DisplayName("구매확정은 재정의한 eventType과 주문 ID인 correlationId를 쓴다")
    void purchaseConfirmedUsesOverrides() {
        OrderItemPurchaseConfirmedEvent event = new OrderItemPurchaseConfirmedEvent(EVENT_ID, OCCURRED_AT,
            new OrderItemPurchaseConfirmedEvent.Data(5001L, 1024L, 1L, 7L, null, 30L, 301L, "110", 2, 50_000L,
                OCCURRED_AT));

        OrderEventEnvelope envelope = OrderEventEnvelope.from(event);

        assertThat(envelope.eventType()).isEqualTo("order-item.purchase-confirmed");
        assertThat(envelope.aggregateId()).isEqualTo("5001");
        assertThat(envelope.correlationId()).isEqualTo("1024");
    }

    @Test
    @DisplayName("JSON은 공통 칸 7개 다음 data 순서, occurredAt은 오프셋 없는 ISO-8601 문자열")
    void serializesInOrder() {
        String json = JsonMapper.builder().build().writeValueAsString(OrderEventEnvelope.from(orderCreated()));

        assertThat(json).isEqualTo("{\"eventId\":\"6f1c2e4a-0b7d-4c1e-9a55-2d3f8e7b1a90\","
            + "\"eventType\":\"order.created\",\"schemaVersion\":1,\"occurredAt\":\"2026-10-29T17:30:00.123456\","
            + "\"producer\":\"order\",\"aggregateId\":\"1024\",\"correlationId\":\"1024\","
            + "\"data\":{\"orderId\":1024,\"memberId\":1,\"items\":[{\"orderItemId\":5001,\"optionId\":301,"
            + "\"quantity\":2}]}}");
    }

    @Test
    @DisplayName("장바구니 담기(#654): 공통 칸 7개(종류 cart.item-added · 발행 order · 대상 줄 ID · 흐름 회원 ID) + data, 자녀 없으면 null")
    void serializesCartItemAdded() {
        CartItemAddedEvent event = new CartItemAddedEvent(EVENT_ID, OCCURRED_AT,
            new CartItemAddedEvent.Data(40L, 1L, null, 10L, 301L, 2, 5));

        String json = JsonMapper.builder().build().writeValueAsString(OrderEventEnvelope.from(event));

        assertThat(json).isEqualTo("{\"eventId\":\"6f1c2e4a-0b7d-4c1e-9a55-2d3f8e7b1a90\","
            + "\"eventType\":\"cart.item-added\",\"schemaVersion\":1,\"occurredAt\":\"2026-10-29T17:30:00.123456\","
            + "\"producer\":\"order\",\"aggregateId\":\"40\",\"correlationId\":\"1\","
            + "\"data\":{\"cartItemId\":40,\"memberId\":1,\"childId\":null,\"productId\":10,\"optionId\":301,"
            + "\"quantity\":2,\"cartQuantity\":5}}");
    }

    private static OrderCreatedEvent orderCreated() {
        return new OrderCreatedEvent(EVENT_ID, OCCURRED_AT,
            new OrderCreatedEvent.Data(1024L, 1L, List.of(new OrderCreatedEvent.Item(5001L, 301L, 2))));
    }
}
