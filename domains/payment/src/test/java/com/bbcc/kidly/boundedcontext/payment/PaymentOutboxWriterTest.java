package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.NOW;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.ORDER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;

import com.bbcc.kidly.boundedcontext.payment.domain.PaymentOutboxMessage;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentOutboxRepository;
import com.bbcc.kidly.boundedcontext.payment.out.kafka.PaymentOutboxWriter;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import java.time.LocalDateTime;
import java.util.List;
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
class PaymentOutboxWriterTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final Long PAYMENT_ID = 1L;
    private static final Long SETTLEMENT_ID = 31L;
    private static final PaymentCompletedEvent COMPLETED = new PaymentCompletedEvent(UUID.randomUUID(), NOW,
        new PaymentCompletedEvent.Data(PAYMENT_ID, ORDER_ID, MEMBER_ID, 10_000L,
            List.of(new PaymentCompletedEvent.Item(10L, 10_000L)), NOW));

    @Mock
    private PaymentOutboxRepository outboxRepository;

    private PaymentOutboxWriter writer;

    @BeforeEach
    void setUp() {
        writer = new PaymentOutboxWriter(outboxRepository, JSON_MAPPER);
    }

    @Test
    @DisplayName("kidly.payment.events로 보낼 메시지를 주문 ID 키 · eventType · eventId로 저장한다")
    void appendToPaymentTopic() {
        PaymentOutboxMessage message = append(COMPLETED);

        assertThat(message.getTopic()).isEqualTo("kidly.payment.events");
        assertThat(message.getMessageKey()).isEqualTo(String.valueOf(ORDER_ID));
        assertThat(message.getEventType()).isEqualTo("payment.completed");
        assertThat(message.getEventId()).isEqualTo(COMPLETED.eventId());
    }

    @Test
    @DisplayName("본문은 공통 칸 7개와 data다. ID는 숫자, 시각은 시간대 없는 ISO 문자열이다")
    void payload() {
        JsonNode body = JSON_MAPPER.readTree(append(COMPLETED).getPayload());

        assertThat(body.propertyNames()).containsExactly("eventId", "eventType", "schemaVersion", "occurredAt",
            "producer", "aggregateId", "correlationId", "data");
        assertThat(body.get("eventId").asString()).isEqualTo(COMPLETED.eventId().toString());
        assertThat(body.get("eventType").asString()).isEqualTo("payment.completed");
        assertThat(body.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(body.get("occurredAt").asString()).isEqualTo("2026-10-12T12:00:00");
        assertThat(body.get("producer").asString()).isEqualTo("payment");
        assertThat(body.get("aggregateId").asString()).isEqualTo(String.valueOf(PAYMENT_ID));
        assertThat(body.get("correlationId").asString()).isEqualTo(String.valueOf(ORDER_ID));
        assertThat(body.get("data").get("orderId").isNumber()).isTrue();
        assertThat(body.get("data").get("orderId").asLong()).isEqualTo(ORDER_ID);
        assertThat(body.get("data").get("items").get(0).get("orderItemId").asLong()).isEqualTo(10L);
    }

    @Test
    @DisplayName("저장하지 않은 결제 실패(결제 ID 없음)도 주문 ID 키로 저장한다")
    void failedWithoutPaymentId() {
        PaymentFailedEvent failed = new PaymentFailedEvent(UUID.randomUUID(), NOW, new PaymentFailedEvent.Data(
            null, ORDER_ID, MEMBER_ID, 10_000L, PaymentFailureCode.AMOUNT_MISMATCH, NOW));

        assertThat(append(failed).getMessageKey()).isEqualTo(String.valueOf(ORDER_ID));
    }

    @Test
    @DisplayName("정산 지급 이벤트는 정산서 ID 키로 저장한다")
    void payoutKey() {
        PaymentPayoutCompletedEvent payout = new PaymentPayoutCompletedEvent(UUID.randomUUID(), NOW,
            new PaymentPayoutCompletedEvent.Data(SETTLEMENT_ID, MEMBER_ID, 270_000L, 5L, NOW));

        PaymentOutboxMessage message = append(payout);

        assertThat(message.getMessageKey()).isEqualTo(String.valueOf(SETTLEMENT_ID));
        assertThat(message.getEventType()).isEqualTo("payment.payout-completed");
    }

    // 저장소에 넘긴 값으로 메시지를 만들어 돌려준다
    private PaymentOutboxMessage append(DomainEvent event) {
        writer.append(event);

        ArgumentCaptor<UUID> eventId = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        then(outboxRepository).should().saveIfAbsent(eventId.capture(), eventType.capture(), topic.capture(),
            messageKey.capture(), payload.capture(), any(LocalDateTime.class));
        return PaymentOutboxMessage.create(eventId.getValue(), eventType.getValue(), topic.getValue(),
            messageKey.getValue(), payload.getValue());
    }
}
