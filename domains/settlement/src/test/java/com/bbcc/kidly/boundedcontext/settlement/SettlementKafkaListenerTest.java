package com.bbcc.kidly.boundedcontext.settlement;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementItemService;
import com.bbcc.kidly.boundedcontext.settlement.app.SettlementPayoutResultService;
import com.bbcc.kidly.boundedcontext.settlement.in.SettlementOrderKafkaListener;
import com.bbcc.kidly.boundedcontext.settlement.in.SettlementPaymentKafkaListener;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import com.bbcc.kidly.shared.payment.domain.PayoutFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutFailedEvent;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.annotation.KafkaListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문 · 결제 토픽에서 받은 메시지를 eventType 헤더로 골라 정산 처리에 넘기는지 본다.
 * 본문은 보내는 쪽과 같은 모양(공통 칸 7개 + data)이고, eventType은 shared 이벤트의 eventType() 규칙에서 만든다.
 */
@ExtendWith(MockitoExtension.class)
class SettlementKafkaListenerTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 10, 0);

    @Mock
    private SettlementItemService settlementItemService;

    @Mock
    private SettlementPayoutResultService settlementPayoutResultService;

    private SettlementOrderKafkaListener orderListener;
    private SettlementPaymentKafkaListener paymentListener;

    @BeforeEach
    void setUp() {
        orderListener = new SettlementOrderKafkaListener(settlementItemService, JSON_MAPPER);
        paymentListener = new SettlementPaymentKafkaListener(settlementPayoutResultService, JSON_MAPPER);
    }

    @Test
    @DisplayName("order-item.purchase-confirmed는 정산 대상 쌓기에 넘기고, 주문의 다른 종류는 읽지 않고 건너뛴다")
    void orderEvents() {
        OrderItemPurchaseConfirmedEvent confirmed = new OrderItemPurchaseConfirmedEvent(UUID.randomUUID(), NOW,
            new OrderItemPurchaseConfirmedEvent.Data(101L, 1L, 2L, 10L, null, 3L, 4L, "110", 2, 50_000L, NOW));

        orderListener.on(record("kidly.order.events", confirmed.eventType(), envelope(confirmed)));
        orderListener.on(record("kidly.order.events", "order.payment-requested", "정산이 받지 않는 종류라 읽지 않는 본문"));

        ArgumentCaptor<OrderItemPurchaseConfirmedEvent.Data> captor =
            ArgumentCaptor.forClass(OrderItemPurchaseConfirmedEvent.Data.class);
        then(settlementItemService).should().register(captor.capture());
        assertThat(captor.getValue()).isEqualTo(confirmed.data());
    }

    @Test
    @DisplayName("payment.payout-completed · payment.payout-failed는 지급 결과 반영에 넘기고, 결제의 다른 종류는 건너뛴다")
    void paymentEvents() {
        PaymentPayoutCompletedEvent completed = new PaymentPayoutCompletedEvent(UUID.randomUUID(), NOW,
            new PaymentPayoutCompletedEvent.Data(31L, 10L, 9_000L, 7L, NOW));
        PaymentPayoutFailedEvent failed = new PaymentPayoutFailedEvent(UUID.randomUUID(), NOW,
            new PaymentPayoutFailedEvent.Data(32L, 10L, 9_000L, PayoutFailureCode.WALLET_NOT_FOUND, NOW));

        paymentListener.on(record("kidly.payment.events", completed.eventType(), envelope(completed)));
        paymentListener.on(record("kidly.payment.events", failed.eventType(), envelope(failed)));
        paymentListener.on(record("kidly.payment.events", "payment.charge-completed", "정산이 받지 않는 종류라 읽지 않는 본문"));

        then(settlementPayoutResultService).should().markPaid(completed.data());
        then(settlementPayoutResultService).should().markPaymentFailed(failed.data());
    }

    @Test
    @DisplayName("받는 종류인데 본문을 읽지 못하면 JacksonException을 던진다(재시도 없이 DLT)")
    void unreadableBody() {
        assertThatThrownBy(() -> paymentListener.on(record("kidly.payment.events", "payment.payout-completed",
            "{\"data\":{\"settlementId\":\"숫자 아님\"}}")))
            .isInstanceOf(JacksonException.class);
        then(settlementPayoutResultService).should(never()).markPaid(any());
    }

    @Test
    @DisplayName("eventType 헤더가 없으면 어떤 메시지인지 알 수 없어 IllegalStateException을 던진다(재시도 없이 DLT)")
    void missingEventType() {
        assertThatThrownBy(() -> orderListener.on(record("kidly.order.events", null, "{}")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("eventType");
    }

    @Test
    @DisplayName("Kafka 리스너는 스위치(kidly.settlement.kafka.consumer.enabled)가 켜져 있을 때만 시작하고, 구독 그룹은 kidly.settlement다")
    void startsBySwitch() throws NoSuchMethodException {
        for (Class<?> listener : List.of(SettlementOrderKafkaListener.class, SettlementPaymentKafkaListener.class)) {
            KafkaListener kafkaListener = listener.getMethod("on", ConsumerRecord.class)
                .getAnnotation(KafkaListener.class);
            assertThat(kafkaListener.autoStartup()).isEqualTo("${kidly.settlement.kafka.consumer.enabled:true}");
            assertThat(kafkaListener.groupId()).isEqualTo("kidly.settlement");
            assertThat(kafkaListener.containerFactory()).isEqualTo("settlementKafkaListenerContainerFactory");
        }
    }

    // 보내는 쪽 본문과 같은 모양: 공통 칸 7개 + data. 이벤트 record에 없는 칸(eventType 등)도 들어 있다
    @SuppressWarnings("unchecked")
    private static String envelope(DomainEvent event) {
        Map<String, Object> fields = JSON_MAPPER.convertValue(event, Map.class);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", fields.get("eventId"));
        body.put("eventType", event.eventType());
        body.put("schemaVersion", event.schemaVersion());
        body.put("occurredAt", fields.get("occurredAt"));
        body.put("producer", event.producer());
        body.put("aggregateId", event.aggregateId());
        body.put("correlationId", event.correlationId());
        body.put("data", fields.get("data"));
        return JSON_MAPPER.writeValueAsString(body);
    }

    private static ConsumerRecord<String, String> record(String topic, String eventType, String body) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(topic, 0, 0L, "1", body);
        if (eventType != null) {
            record.headers().add("eventType", eventType.getBytes(UTF_8));
        }
        record.headers().add("eventId", UUID.randomUUID().toString().getBytes(UTF_8));
        return record;
    }
}
