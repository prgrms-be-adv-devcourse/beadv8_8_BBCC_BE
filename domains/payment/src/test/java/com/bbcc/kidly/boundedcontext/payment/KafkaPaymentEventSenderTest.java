package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.ORDER_ID;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentEventSendException;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentOutboxMessage;
import com.bbcc.kidly.boundedcontext.payment.out.kafka.KafkaPaymentEventSender;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class KafkaPaymentEventSenderTest {

    private static final Duration SEND_TIMEOUT = Duration.ofMillis(200);

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaPaymentEventSender sender;

    @BeforeEach
    void setUp() {
        sender = new KafkaPaymentEventSender(kafkaTemplate, SEND_TIMEOUT);
    }

    @Test
    @DisplayName("저장된 토픽 · 키 · 본문 그대로, eventType · eventId 헤더를 붙여 보낸다")
    @SuppressWarnings("unchecked")
    void sendStoredMessage() {
        PaymentOutboxMessage message = PaymentFixture.outboxMessage();
        given(kafkaTemplate.send(any(ProducerRecord.class)))
            .willReturn(CompletableFuture.completedFuture(new SendResult<>(null, null)));

        sender.send(message);

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        then(kafkaTemplate).should().send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();
        assertThat(record.topic()).isEqualTo("kidly.payment.events");
        assertThat(record.key()).isEqualTo(String.valueOf(ORDER_ID));
        assertThat(record.value()).isEqualTo(message.getPayload());
        assertThat(header(record, "eventType")).isEqualTo("payment.completed");
        assertThat(header(record, "eventId")).isEqualTo(message.getEventId().toString());
        assertThat(record.headers().lastHeader("__TypeId__")).isNull();
    }

    @Test
    @DisplayName("보내기 전에 실패하면 Kafka 원인을 담아 예외를 던진다. 본문은 담지 않는다")
    void failBeforeSend() {
        PaymentOutboxMessage message = PaymentFixture.outboxMessage();
        given(kafkaTemplate.send(any(ProducerRecord.class))).willThrow(
            new KafkaException("Send failed", new TimeoutException("Topic not present in metadata after 3000 ms.")));

        assertThatThrownBy(() -> sender.send(message))
            .isInstanceOf(PaymentEventSendException.class)
            .hasMessageContaining("eventType=payment.completed")
            .hasMessageContaining("eventId=" + message.getEventId())
            .hasMessageContaining("key=" + ORDER_ID)
            .hasMessageContaining("after 3000 ms")
            .hasMessageNotContaining(message.getPayload());
    }

    @Test
    @DisplayName("브로커가 실패로 답하면 예외를 던진다")
    void failAfterSend() {
        given(kafkaTemplate.send(any(ProducerRecord.class)))
            .willReturn(CompletableFuture.failedFuture(new KafkaException("Failed to send",
                new TimeoutException("Expiring 1 record(s)"))));

        assertThatThrownBy(() -> sender.send(PaymentFixture.outboxMessage()))
            .isInstanceOf(PaymentEventSendException.class)
            .hasMessageContaining("Expiring 1 record(s)");
    }

    @Test
    @DisplayName("응답이 시간 안에 오지 않으면 예외를 던진다")
    void timeout() {
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(new CompletableFuture<>());

        assertThatThrownBy(() -> sender.send(PaymentFixture.outboxMessage()))
            .isInstanceOf(PaymentEventSendException.class)
            .hasMessageContaining("응답 대기 시간 200ms 초과");
    }

    @Test
    @DisplayName("앱이 내려갈 때 결제 전용 템플릿을 닫는다")
    void destroy() {
        sender.destroy();

        then(kafkaTemplate).should().destroy();
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), UTF_8);
    }
}
