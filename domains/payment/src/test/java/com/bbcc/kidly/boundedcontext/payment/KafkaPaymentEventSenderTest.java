package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.NOW;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.ORDER_ID;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bbcc.kidly.boundedcontext.payment.out.kafka.KafkaPaymentEventSender;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class KafkaPaymentEventSenderTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final Long PAYMENT_ID = 1L;
    private static final Long SETTLEMENT_ID = 31L;
    private static final PaymentCompletedEvent COMPLETED = new PaymentCompletedEvent(UUID.randomUUID(), NOW,
        new PaymentCompletedEvent.Data(PAYMENT_ID, ORDER_ID, MEMBER_ID, 10_000L,
            List.of(new PaymentCompletedEvent.Item(10L, 10_000L)), NOW));

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @DisplayName("kidly.payment.events에 주문 ID 키로, eventType · eventId 헤더를 붙여 보낸다")
    void sendToPaymentTopic() {
        ProducerRecord<String, String> record = send(COMPLETED);

        assertThat(record.topic()).isEqualTo("kidly.payment.events");
        assertThat(record.key()).isEqualTo(String.valueOf(ORDER_ID));
        assertThat(header(record, "eventType")).isEqualTo("payment.completed");
        assertThat(header(record, "eventId")).isEqualTo(COMPLETED.eventId().toString());
        assertThat(record.headers().lastHeader("__TypeId__")).isNull();
    }

    @Test
    @DisplayName("본문은 공통 칸 7개와 data다. ID는 숫자, 시각은 시간대 없는 ISO 문자열이다")
    void body() {
        JsonNode body = JSON_MAPPER.readTree(send(COMPLETED).value());

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
    @DisplayName("저장하지 않은 결제 실패(결제 ID 없음)도 주문 ID 키로 보낸다")
    void failedWithoutPaymentId() {
        PaymentFailedEvent failed = new PaymentFailedEvent(UUID.randomUUID(), NOW, new PaymentFailedEvent.Data(
            null, ORDER_ID, MEMBER_ID, 10_000L, PaymentFailureCode.AMOUNT_MISMATCH, NOW));

        assertThat(send(failed).key()).isEqualTo(String.valueOf(ORDER_ID));
    }

    @Test
    @DisplayName("정산 지급 이벤트는 정산서 ID 키로 보낸다")
    void payoutKey() {
        PaymentPayoutCompletedEvent payout = new PaymentPayoutCompletedEvent(UUID.randomUUID(), NOW,
            new PaymentPayoutCompletedEvent.Data(SETTLEMENT_ID, MEMBER_ID, 270_000L, 5L, NOW));

        ProducerRecord<String, String> record = send(payout);

        assertThat(record.key()).isEqualTo(String.valueOf(SETTLEMENT_ID));
        assertThat(header(record, "eventType")).isEqualTo("payment.payout-completed");
    }

    @Test
    @DisplayName("보내기 전에 실패해도 예외를 던지지 않고 본문 없이 ERROR 로그를 남긴다")
    void swallowImmediateFailure(CapturedOutput output) {
        given(kafkaTemplate.send(any(ProducerRecord.class))).willThrow(new KafkaException("브로커 없음"));

        assertThatCode(() -> sender().send(COMPLETED)).doesNotThrowAnyException();

        assertFailureLogged(output);
    }

    @Test
    @DisplayName("보낸 뒤 실패해도 본문 없이 ERROR 로그를 남긴다")
    void logAsyncFailure(CapturedOutput output) {
        given(kafkaTemplate.send(any(ProducerRecord.class)))
            .willReturn(CompletableFuture.failedFuture(new KafkaException("전송 시간 초과")));

        assertThatCode(() -> sender().send(COMPLETED)).doesNotThrowAnyException();

        assertFailureLogged(output);
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, String> send(DomainEvent event) {
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(CompletableFuture.completedFuture(null));

        sender().send(event);

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        then(kafkaTemplate).should().send(captor.capture());
        return captor.getValue();
    }

    private KafkaPaymentEventSender sender() {
        return new KafkaPaymentEventSender(kafkaTemplate, JSON_MAPPER);
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), UTF_8);
    }

    private static void assertFailureLogged(CapturedOutput output) {
        assertThat(output.getOut())
            .contains("ERROR")
            .contains("eventType=payment.completed")
            .contains("eventId=" + COMPLETED.eventId())
            .contains("key=" + ORDER_ID)
            .doesNotContain("memberId")
            .doesNotContain(COMPLETED.data().toString());
    }
}
