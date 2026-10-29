package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxRepository;
import com.bbcc.kidly.boundedcontext.product.out.outbox.ProductOutboxWriter;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ProductOutboxWriterTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000077");

    private static final StockDeductedEvent STOCK_DEDUCTED = new StockDeductedEvent(
        EVENT_ID, LocalDateTime.of(2026, 10, 29, 12, 0, 1), new StockDeductedEvent.Data(77L));

    @Mock
    private ProductOutboxRepository outboxRepository;

    @Test
    @DisplayName("이벤트를 kidly.product.events로 보낼 메시지로 저장한다. 키는 correlationId, 종류와 ID는 이벤트 값이다")
    void appendMessageWithTopicKeyAndType() {
        ProductOutboxMessage saved = appendAndCapture();

        assertThat(saved)
            .extracting(
                ProductOutboxMessage::getTopic,
                ProductOutboxMessage::getMessageKey,
                ProductOutboxMessage::getEventType,
                ProductOutboxMessage::getEventId)
            .containsExactly("kidly.product.events", "77", "stock.deducted", EVENT_ID);
    }

    @Test
    @DisplayName("본문은 공통 칸 7개 다음에 data를 둔 JSON이고, 발생 시각은 시간대 표시 없는 한국 시간 문자열이다")
    void appendBodyWithCommonFieldsAndData() {
        JsonNode body = JSON_MAPPER.readTree(appendAndCapture().getPayload());

        assertThat(body.propertyNames()).containsExactly("eventId", "eventType", "schemaVersion", "occurredAt",
            "producer", "aggregateId", "correlationId", "data");
        assertThat(body.get("eventId").asString()).isEqualTo(EVENT_ID.toString());
        assertThat(body.get("eventType").asString()).isEqualTo("stock.deducted");
        assertThat(body.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(body.get("occurredAt").asString()).isEqualTo("2026-10-29T12:00:01");
        assertThat(body.get("producer").asString()).isEqualTo("product");
        assertThat(body.get("aggregateId").asString()).isEqualTo("77");
        assertThat(body.get("correlationId").asString()).isEqualTo("77");
        assertThat(body.get("data").get("orderId").asLong()).isEqualTo(77L);
    }

    private ProductOutboxMessage appendAndCapture() {
        new ProductOutboxWriter(outboxRepository, JSON_MAPPER).append(STOCK_DEDUCTED);
        ArgumentCaptor<ProductOutboxMessage> captor = ArgumentCaptor.forClass(ProductOutboxMessage.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }
}
