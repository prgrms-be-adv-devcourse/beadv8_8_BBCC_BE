package com.bbcc.kidly.boundedcontext.product;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.bbcc.kidly.boundedcontext.product.app.port.outbox.ProductEventSendException;
import com.bbcc.kidly.boundedcontext.product.app.port.outbox.ProductEventSender;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.testcontainers.kafka.KafkaContainer;

/**
 * 상품 아웃박스 메시지가 실제 브로커의 kidly.product.events에 규칙대로(키 · 헤더 · 본문) 도착하는지 본다.
 * 다른 테스트의 메시지도 같은 토픽에 쌓이므로 헤더 eventId로 이 테스트의 메시지를 찾는다.
 */
@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class KafkaProductEventSenderIntegrationTest {

    private static final String TOPIC = "kidly.product.events";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private ProductEventSender productEventSender;

    @Autowired
    private KafkaContainer kafka;

    private KafkaConsumer<String, String> consumer;
    private final List<ConsumerRecord<String, String>> received = new ArrayList<>();

    @BeforeEach
    void subscribe() {
        consumer = new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "product-kafka-test-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumer.subscribe(List.of(TOPIC));
    }

    @AfterEach
    void close() {
        consumer.close();
    }

    @Test
    @DisplayName("보낸 메시지가 파티션 3개인 kidly.product.events에 같은 키 · 헤더 2개 · 본문으로 도착한다")
    void arriveAtProductTopic() {
        UUID eventId = UUID.randomUUID();
        ProductOutboxMessage message = ProductOutboxMessage.create(ProductOutboxMessageFixture.productChanged(eventId));

        productEventSender.send(message);

        ConsumerRecord<String, String> record = awaitRecord(eventId);
        assertThat(record.key()).isEqualTo("1");
        assertThat(record.headers().toArray()).extracting(Header::key).containsExactly("eventType", "eventId");
        assertThat(header(record, "eventType")).isEqualTo("product.changed");
        assertThat(record.value()).isEqualTo(ProductOutboxMessageFixture.PAYLOAD);
        assertThat(consumer.partitionsFor(TOPIC)).hasSize(3);
    }

    @Test
    @DisplayName("없는 토픽으로 보내면 몇 초 안에 전송 예외가 나고, 로그와 예외 메시지에 본문을 남기지 않는다")
    void failFastWithoutBodyInLogs(CapturedOutput output) {
        // 브로커의 토픽 자동 생성이 꺼져 있어 선언하지 않은 토픽에는 보내지 못한다
        UUID eventId = UUID.randomUUID();
        ProductOutboxMessage message = ProductOutboxMessage.create(new ProductOutboxMessage.CreateInfo(
            eventId, "product.changed", "kidly.product.missing-" + eventId, "1", ProductOutboxMessageFixture.PAYLOAD));

        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> assertThatThrownBy(
            () -> productEventSender.send(message))
            .isInstanceOf(ProductEventSendException.class)
            .hasMessageContaining("eventId=" + eventId)
            .hasMessageNotContaining(ProductOutboxMessageFixture.PAYLOAD));
        assertThat(output.getAll()).doesNotContain(ProductOutboxMessageFixture.PAYLOAD);
    }

    private ConsumerRecord<String, String> awaitRecord(UUID eventId) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            consumer.poll(Duration.ofMillis(200)).forEach(received::add);
            Optional<ConsumerRecord<String, String>> found = received.stream()
                .filter(record -> eventId.toString().equals(header(record, "eventId")))
                .findFirst();
            if (found.isPresent()) {
                return found.get();
            }
        }
        throw new AssertionError(TIMEOUT.toSeconds() + "초 안에 eventId=" + eventId + " 메시지가 오지 않았습니다");
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), UTF_8);
    }
}
