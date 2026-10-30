package com.bbcc.kidly.boundedcontext.settlement;

import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.NOW;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.PERIOD_END;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.PERIOD_START;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;

import com.bbcc.kidly.boundedcontext.settlement.domain.outbox.SettlementOutboxMessage;
import com.bbcc.kidly.boundedcontext.settlement.domain.outbox.SettlementOutboxRepository;
import com.bbcc.kidly.boundedcontext.settlement.out.kafka.SettlementOutboxWriter;
import com.bbcc.kidly.shared.settlement.event.SettlementPayoutRequestedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class SettlementOutboxWriterTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final Long SETTLEMENT_ID = 31L;
    private static final SettlementPayoutRequestedEvent REQUESTED = new SettlementPayoutRequestedEvent(
        UUID.randomUUID(), NOW, new SettlementPayoutRequestedEvent.Data(SETTLEMENT_ID, SELLER_ID, 270_000L,
            PERIOD_START, PERIOD_END));

    @Mock
    private SettlementOutboxRepository outboxRepository;

    private SettlementOutboxWriter writer;

    @BeforeEach
    void setUp() {
        writer = new SettlementOutboxWriter(outboxRepository, JSON_MAPPER);
    }

    @Test
    @DisplayName("kidly.settlement.events로 보낼 메시지를 정산서 ID 키 · eventType · eventId로 저장한다")
    void appendToSettlementTopic() {
        SettlementOutboxMessage message = append();

        assertThat(message.getTopic()).isEqualTo("kidly.settlement.events");
        assertThat(message.getMessageKey()).isEqualTo(String.valueOf(SETTLEMENT_ID));
        assertThat(message.getEventType()).isEqualTo("settlement.payout-requested");
        assertThat(message.getEventId()).isEqualTo(REQUESTED.eventId());
    }

    @Test
    @DisplayName("본문은 공통 칸 7개와 data다. ID는 숫자, 날짜와 시각은 시간대 없는 ISO 문자열이다")
    void payload() {
        JsonNode body = JSON_MAPPER.readTree(append().getPayload());

        assertThat(body.propertyNames()).containsExactly("eventId", "eventType", "schemaVersion", "occurredAt",
            "producer", "aggregateId", "correlationId", "data");
        assertThat(body.get("eventId").asString()).isEqualTo(REQUESTED.eventId().toString());
        assertThat(body.get("eventType").asString()).isEqualTo("settlement.payout-requested");
        assertThat(body.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(body.get("occurredAt").asString()).isEqualTo("2026-11-01T00:00:00");
        assertThat(body.get("producer").asString()).isEqualTo("settlement");
        assertThat(body.get("aggregateId").asString()).isEqualTo(String.valueOf(SETTLEMENT_ID));
        assertThat(body.get("correlationId").asString()).isEqualTo(String.valueOf(SETTLEMENT_ID));
        JsonNode data = body.get("data");
        assertThat(data.get("settlementId").isNumber()).isTrue();
        assertThat(data.get("settlementId").asLong()).isEqualTo(SETTLEMENT_ID);
        assertThat(data.get("sellerId").asLong()).isEqualTo(SELLER_ID);
        assertThat(data.get("amount").asLong()).isEqualTo(270_000L);
        assertThat(data.get("periodStartOn").asString()).isEqualTo("2026-10-01");
        assertThat(data.get("periodEndOn").asString()).isEqualTo("2026-10-31");
    }

    // 저장소에 넘긴 값으로 메시지를 만들어 돌려준다
    private SettlementOutboxMessage append() {
        writer.append(REQUESTED);

        ArgumentCaptor<UUID> eventId = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        then(outboxRepository).should().saveIfAbsent(eventId.capture(), eventType.capture(), topic.capture(),
            messageKey.capture(), payload.capture(), any(LocalDateTime.class));
        return SettlementOutboxMessage.create(eventId.getValue(), eventType.getValue(), topic.getValue(),
            messageKey.getValue(), payload.getValue());
    }
}
