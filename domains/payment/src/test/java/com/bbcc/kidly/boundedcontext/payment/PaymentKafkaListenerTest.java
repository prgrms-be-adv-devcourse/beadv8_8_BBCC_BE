package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.ORDER_ID;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.payment.app.PaymentEventHandler;
import com.bbcc.kidly.boundedcontext.payment.in.event.PaymentMemberEventListener;
import com.bbcc.kidly.boundedcontext.payment.in.event.PaymentOrderEventListener;
import com.bbcc.kidly.boundedcontext.payment.in.event.PaymentSettlementEventListener;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import com.bbcc.kidly.shared.settlement.event.SettlementPayoutRequestedEvent;
import java.lang.reflect.Method;
import java.time.LocalDate;
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
 * 회원 · 주문 토픽에서 받은 메시지를 eventType 헤더로 골라 결제 처리에 넘기는지 본다(#593).
 * 본문은 보내는 쪽과 같은 모양(공통 칸 7개 + data)이다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentKafkaListenerTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 10, 0);

    @Mock
    private PaymentEventHandler handler;

    private PaymentMemberEventListener memberListener;
    private PaymentOrderEventListener orderListener;
    private PaymentSettlementEventListener settlementListener;

    @BeforeEach
    void setUp() {
        memberListener = new PaymentMemberEventListener(handler, JSON_MAPPER);
        orderListener = new PaymentOrderEventListener(handler, JSON_MAPPER);
        settlementListener = new PaymentSettlementEventListener(handler, JSON_MAPPER);
    }

    @Test
    @DisplayName("member.registered는 지갑 만들기에 넘기고, 회원의 다른 종류는 읽지 않고 건너뛴다")
    void memberEvents() {
        MemberRegisteredEvent registered = new MemberRegisteredEvent(UUID.randomUUID(), NOW,
            new MemberRegisteredEvent.Data(MEMBER_ID, "buyer@kidly.test"));

        memberListener.on(record("kidly.member.events", "member.registered",
            envelope(registered, "member.registered")));
        memberListener.on(record("kidly.member.events", "member.changed", "결제가 받지 않는 종류라 읽지 않는 본문"));

        ArgumentCaptor<MemberRegisteredEvent> captor = ArgumentCaptor.forClass(MemberRegisteredEvent.class);
        then(handler).should().memberRegistered(captor.capture());
        assertThat(captor.getValue().data().memberId()).isEqualTo(MEMBER_ID);
        assertThat(captor.getValue().eventId()).isEqualTo(registered.eventId());
    }

    @Test
    @DisplayName("order.payment-requested · order.cancelled는 결제 · 취소 처리에 넘기고, 주문의 다른 종류는 건너뛴다")
    void orderEvents() {
        OrderPaymentRequestedEvent requested = new OrderPaymentRequestedEvent(UUID.randomUUID(), NOW,
            new OrderPaymentRequestedEvent.Data(ORDER_ID, MEMBER_ID, 15_000L,
                List.of(new OrderPaymentRequestedEvent.Item(1000L, 15_000L))));
        OrderCancelledEvent cancelled = new OrderCancelledEvent(UUID.randomUUID(), NOW,
            new OrderCancelledEvent.Data(ORDER_ID, MEMBER_ID, "PAYMENT_EXPIRED",
                List.of(new OrderCancelledEvent.Item(1000L, 1L, 1))));

        orderListener.on(record("kidly.order.events", "order.payment-requested",
            envelope(requested, "order.payment-requested")));
        orderListener.on(record("kidly.order.events", "order.cancelled", envelope(cancelled, "order.cancelled")));
        orderListener.on(record("kidly.order.events", "order.created", "결제가 받지 않는 종류라 읽지 않는 본문"));

        ArgumentCaptor<OrderPaymentRequestedEvent> requestedCaptor =
            ArgumentCaptor.forClass(OrderPaymentRequestedEvent.class);
        then(handler).should().orderPaymentRequested(requestedCaptor.capture());
        assertThat(requestedCaptor.getValue().data().amount()).isEqualTo(15_000L);
        ArgumentCaptor<OrderCancelledEvent> cancelledCaptor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        then(handler).should().orderCancelled(cancelledCaptor.capture());
        assertThat(cancelledCaptor.getValue().data().reason()).isEqualTo("PAYMENT_EXPIRED");
    }

    @Test
    @DisplayName("settlement.payout-requested는 정산 입금에 넘기고, 정산의 다른 종류는 건너뛴다")
    void settlementEvents() {
        SettlementPayoutRequestedEvent requested = new SettlementPayoutRequestedEvent(UUID.randomUUID(), NOW,
            new SettlementPayoutRequestedEvent.Data(31L, MEMBER_ID, 270_000L, LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31)));

        settlementListener.on(record("kidly.settlement.events", "settlement.payout-requested",
            envelope(requested, "settlement.payout-requested")));
        settlementListener.on(record("kidly.settlement.events", "settlement.closed", "결제가 받지 않는 종류라 읽지 않는 본문"));

        ArgumentCaptor<SettlementPayoutRequestedEvent> captor =
            ArgumentCaptor.forClass(SettlementPayoutRequestedEvent.class);
        then(handler).should().settlementPayoutRequested(captor.capture());
        assertThat(captor.getValue().data().settlementId()).isEqualTo(31L);
        assertThat(captor.getValue().data().amount()).isEqualTo(270_000L);
    }

    @Test
    @DisplayName("정산 리스너는 별도 스위치(기본 꺼짐)로 시작한다. 정산이 Kafka로 보내기 전에는 켜지 않는다")
    void settlementStartsBySeparateSwitch() throws NoSuchMethodException {
        KafkaListener kafkaListener = PaymentSettlementEventListener.class.getMethod("on", ConsumerRecord.class)
            .getAnnotation(KafkaListener.class);

        assertThat(kafkaListener.autoStartup()).isEqualTo("${kidly.payment.kafka.settlement-consumer.enabled:false}");
        assertThat(kafkaListener.topics()).containsExactly("kidly.settlement.events");
        assertThat(kafkaListener.groupId()).isEqualTo("kidly.payment");
    }

    @Test
    @DisplayName("받는 종류인데 본문을 읽지 못하면 JacksonException을 던진다(재시도 없이 DLT)")
    void unreadableBody() {
        assertThatThrownBy(() -> orderListener.on(record("kidly.order.events", "order.payment-requested",
            "{\"data\":{\"orderId\":\"숫자 아님\"}}")))
            .isInstanceOf(JacksonException.class);
        then(handler).should(never()).orderPaymentRequested(any());
    }

    @Test
    @DisplayName("eventType 헤더가 없으면 어떤 메시지인지 알 수 없어 IllegalArgumentException을 던진다(재시도 없이 DLT)")
    void missingEventType() {
        assertThatThrownBy(() -> memberListener.on(record("kidly.member.events", null, "{}")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("eventType");
    }

    @Test
    @DisplayName("Kafka 리스너는 스위치(kidly.payment.kafka.consumer.enabled)가 켜져 있을 때만 시작하고, 구독 그룹은 kidly.payment다")
    void startsBySwitch() throws NoSuchMethodException {
        for (Class<?> listener : List.of(PaymentMemberEventListener.class, PaymentOrderEventListener.class)) {
            Method on = listener.getMethod("on", ConsumerRecord.class);
            KafkaListener kafkaListener = on.getAnnotation(KafkaListener.class);
            assertThat(kafkaListener.autoStartup()).isEqualTo("${kidly.payment.kafka.consumer.enabled:true}");
            assertThat(kafkaListener.groupId()).isEqualTo("kidly.payment");
        }
    }

    // 보내는 쪽 본문과 같은 모양: 공통 칸 7개 + data. 이벤트 record에 없는 칸(eventType 등)도 들어 있다
    @SuppressWarnings("unchecked")
    private static String envelope(DomainEvent event, String eventType) {
        Map<String, Object> fields = JSON_MAPPER.convertValue(event, Map.class);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", fields.get("eventId"));
        body.put("eventType", eventType);
        body.put("schemaVersion", 1);
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
