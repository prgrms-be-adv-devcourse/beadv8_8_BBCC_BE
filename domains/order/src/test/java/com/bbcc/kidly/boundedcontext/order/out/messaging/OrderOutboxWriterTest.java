package com.bbcc.kidly.boundedcontext.order.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.order.out.persistence.JpaOrderOutboxRepository;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class OrderOutboxWriterTest {

    private static final UUID EVENT_ID = UUID.fromString("6f1c2e4a-0b7d-4c1e-9a55-2d3f8e7b1a90");
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 29, 17, 30);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private JpaOrderOutboxRepository outboxRepository;

    private OrderOutboxWriter writer;

    @BeforeEach
    void setUp() {
        writer = new OrderOutboxWriter(outboxRepository, jsonMapper);
    }

    @Test
    @DisplayName("이벤트를 kidly.order.events · 키 correlationId · 본문 envelope JSON인 보내지 않은 메시지로 저장한다")
    void savesUnsentMessage() {
        OrderItemPurchaseConfirmedEvent event = new OrderItemPurchaseConfirmedEvent(EVENT_ID, OCCURRED_AT,
            new OrderItemPurchaseConfirmedEvent.Data(5001L, 1024L, 1L, 7L, null, 30L, 301L, "110", 2, 50_000L,
                OCCURRED_AT));

        writer.append(event);

        ArgumentCaptor<OrderOutboxMessage> saved = ArgumentCaptor.forClass(OrderOutboxMessage.class);
        verify(outboxRepository).save(saved.capture());
        OrderOutboxMessage message = saved.getValue();
        assertThat(message.getEventId()).isEqualTo(EVENT_ID);
        assertThat(message.getEventType()).isEqualTo("order-item.purchase-confirmed");
        assertThat(message.getTopic()).isEqualTo("kidly.order.events");
        assertThat(message.getMessageKey()).as("주문상품 이벤트도 키는 주문 ID").isEqualTo("1024");
        assertThat(message.getPayload()).isEqualTo(jsonMapper.writeValueAsString(OrderEventEnvelope.from(event)));
        assertThat(message.getSentAt()).isNull();
    }
}
