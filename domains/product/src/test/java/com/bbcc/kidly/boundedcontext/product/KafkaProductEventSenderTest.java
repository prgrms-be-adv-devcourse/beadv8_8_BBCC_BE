package com.bbcc.kidly.boundedcontext.product;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bbcc.kidly.boundedcontext.product.app.port.outbox.ProductEventSendException;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;
import com.bbcc.kidly.boundedcontext.product.out.outbox.KafkaProductEventSender;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaProducerException;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class KafkaProductEventSenderTest {

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(1);

    private static final ProductOutboxMessage MESSAGE =
        ProductOutboxMessage.create(ProductOutboxMessageFixture.productChanged());

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @DisplayName("메시지의 토픽 · 키로 본문을 그대로 보내고, 헤더는 eventType · eventId 두 개만 붙인다")
    @SuppressWarnings("unchecked")
    void sendWithTopicKeyBodyAndHeaders() {
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(CompletableFuture.completedFuture(null));

        sender().send(MESSAGE);

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        then(kafkaTemplate).should().send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();
        assertThat(record.topic()).isEqualTo("kidly.product.events");
        assertThat(record.key()).isEqualTo("1");
        assertThat(record.value()).isEqualTo(ProductOutboxMessageFixture.PAYLOAD);
        assertThat(record.headers().toArray()).extracting(header -> header.key())
            .containsExactly("eventType", "eventId");
        assertThat(header(record, "eventType")).isEqualTo("product.changed");
        assertThat(header(record, "eventId")).isEqualTo(ProductOutboxMessageFixture.EVENT_ID.toString());
    }

    @Test
    @DisplayName("보내기 전에 실패하면 원인을 담은 전송 예외를 던지고, 예외 메시지에 본문을 넣지 않는다")
    @SuppressWarnings("unchecked")
    void throwWhenSendFailsBeforeSending() {
        given(kafkaTemplate.send(any(ProducerRecord.class))).willThrow(new KafkaException("브로커 연결 실패"));

        assertThatThrownBy(() -> sender().send(MESSAGE))
            .isInstanceOf(ProductEventSendException.class)
            .hasMessageContaining("브로커 연결 실패")
            .hasMessageNotContaining(ProductOutboxMessageFixture.PAYLOAD);
    }

    @Test
    @DisplayName("보낸 뒤 브로커 응답이 실패로 오면 Kafka 클라이언트가 알려 준 원인을 담은 전송 예외를 던진다")
    @SuppressWarnings("unchecked")
    void throwWithClientCauseWhenSendFailsAfterSending() {
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(CompletableFuture.failedFuture(
            new KafkaProducerException(null, "Failed to send",
                new TimeoutException("Expiring 1 record(s) for kidly.product.events-0"))));

        assertThatThrownBy(() -> sender().send(MESSAGE))
            .isInstanceOf(ProductEventSendException.class)
            .hasMessageContaining("Expiring 1 record(s) for kidly.product.events-0")
            .hasMessageNotContaining(ProductOutboxMessageFixture.PAYLOAD);
    }

    private KafkaProductEventSender sender() {
        return new KafkaProductEventSender(kafkaTemplate, SEND_TIMEOUT);
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), UTF_8);
    }
}
