package com.bbcc.kidly.boundedcontext.notification;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.notification.app.InboxNotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.ProductNameService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.InboxNotificationView;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * product.changed를 kidly.product.events로 받아 상품 이름 복사본을 저장한다(#851).
 * 이 서비스에는 상품 도메인이 없으므로(상품 서비스) 상품이 보내는 형식(키 correlationId = 상품 ID, 헤더 eventType · eventId,
 * 본문 공통 칸 7개 + data)으로 토픽에 직접 넣는다. 리스너는 이 테스트 안에서만 켜고 끈다.
 * 앞 메시지를 다 처리했는지는 같은 키(같은 파티션)로 뒤에 보낸 표시 메시지가 저장됐는지로 본다. 표시 메시지는 data의 상품 ID만 다르다.
 */
@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class NotificationProductEventKafkaIntegrationTest {

    private static final String TOPIC = "kidly.product.events";
    private static final String DLT = "kidly.product.events.notification.dlt";
    private static final String LISTENER_ID = "notificationProductEventListener";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 11, 5, 9, 0);

    // 재시도는 1초 간격 3번이라, 재시도했다면 DLT에 들어가기까지 3초 이상 걸린다
    private static final long RETRY_BACKOFF_TOTAL_MS = 3_000;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ProductNameService productNameService;

    @Autowired
    private InboxNotificationService inboxNotificationService;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private KafkaContainer kafka;

    private final Long productId = randomId();
    private final Long markerProductId = randomId();
    private final Long sellerId = randomId();

    private MessageListenerContainer listener;

    // 읽기 시작 위치가 latest라 파티션을 배정받기 전에 보낸 메시지는 받지 못한다
    @BeforeEach
    void startListener() {
        listener = kafkaListenerEndpointRegistry.getListenerContainer(LISTENER_ID);
        listener.start();
        AsyncWait.until(TIMEOUT, () -> !listener.getAssignedPartitions().isEmpty());
    }

    @AfterEach
    void stopListener() {
        listener.stop();
    }

    @Test
    @DisplayName("상품이 바뀌면 Kafka로 받아 상품 이름을 저장한다")
    void saveProductName() throws Exception {
        send(productChanged(productId, "곰돌이 티셔츠", OCCURRED_AT));

        AsyncWait.until(TIMEOUT, () -> nameOf(productId).isPresent());
        assertThat(nameOf(productId)).contains("곰돌이 티셔츠");
    }

    @Test
    @DisplayName("늦게 온 옛 메시지는 무시하고, 같은 메시지를 두 번 받아도 실패하지 않는다")
    void ignoreOlderAndDuplicate() throws Exception {
        var latest = productChanged(productId, "곰돌이 반팔 티셔츠", OCCURRED_AT.plusMinutes(1));

        send(latest);
        send(productChanged(productId, "옛 이름", OCCURRED_AT));
        send(latest);
        sendMarker();

        AsyncWait.until(TIMEOUT, () -> nameOf(markerProductId).isPresent());
        assertThat(nameOf(productId)).contains("곰돌이 반팔 티셔츠");
        assertThat(deadLetters()).isEmpty();
    }

    @Test
    @DisplayName("관리자가 판매를 중지하면 Kafka로 받아 판매자 알림함에 한 건 남기고, 같은 메시지를 다시 받아도 한 건이다")
    void stoppedByAdmin() throws Exception {
        var stopped = productChanged(productId, "곰돌이 티셔츠", OCCURRED_AT.plusMinutes(1), true);

        send(productChanged(productId, "곰돌이 티셔츠", OCCURRED_AT));
        send(stopped);
        send(stopped);
        sendMarker();

        AsyncWait.until(TIMEOUT, () -> nameOf(markerProductId).isPresent());
        assertThat(inboxNotificationService.findMine(sellerId, 0, 10).getContent())
            .extracting(InboxNotificationView::type, InboxNotificationView::linkPath)
            .containsExactly(tuple("PRODUCT_STOPPED_BY_ADMIN", "/center/products/" + productId));
    }

    @Test
    @DisplayName("알림이 받지 않는 종류는 건너뛰고 DLT로 보내지 않는다")
    void skipOtherEventType() throws Exception {
        sendRaw("product.deleted", UUID.randomUUID(), "받지 않는 종류라 읽지 않는 본문");
        sendMarker();

        AsyncWait.until(TIMEOUT, () -> nameOf(markerProductId).isPresent());
        assertThat(deadLetters()).isEmpty();
    }

    @Test
    @DisplayName("받는 종류인데 본문을 읽지 못하면 재시도 없이 DLT로 보내고, 로그에 본문을 남기지 않는다")
    void deadLetterUnreadableBody(CapturedOutput output) throws Exception {
        var name = "읽지 못한 상품-" + UUID.randomUUID();
        var body = "{\"data\":{\"productId\":\"숫자 아님\",\"name\":\"" + name + "\"}}";

        var sent = sendRaw("product.changed", UUID.randomUUID(), body);

        AsyncWait.until(TIMEOUT, () -> !deadLetters().isEmpty());
        assertThat(deadLetters()).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.value()).isEqualTo(body);
            assertThat(deadLetter.timestamp() - sent.timestamp()).isLessThan(RETRY_BACKOFF_TOTAL_MS);
        });
        assertThat(output.getOut()).contains("key=" + productId).doesNotContain(name);
    }

    private Optional<String> nameOf(Long id) {
        return productNameService.findName(id);
    }

    // 이 테스트의 키(상품 ID)로 다른 상품의 메시지를 보낸다. 같은 파티션이라 이것이 저장됐으면 앞 메시지는 다 처리됐다
    private void sendMarker() throws Exception {
        send(productChanged(markerProductId, "표시 상품", OCCURRED_AT), productId);
    }

    private ProductChangedEvent productChanged(Long id, String name, LocalDateTime occurredAt) {
        return productChanged(id, name, occurredAt, false);
    }

    private ProductChangedEvent productChanged(Long id, String name, LocalDateTime occurredAt, boolean stoppedByAdmin) {
        return new ProductChangedEvent(UUID.randomUUID(), occurredAt, new ProductChangedEvent.Data(
            id, sellerId, name, 1L, stoppedByAdmin ? ProductStatus.STOPPED : ProductStatus.ON_SALE, null, OCCURRED_AT,
            false, List.of(), List.of(), null, null, List.of(), List.of(), stoppedByAdmin));
    }

    private void send(ProductChangedEvent event) throws Exception {
        send(event, event.data().productId());
    }

    // 상품이 보내는 형식 그대로 넣는다
    private void send(ProductChangedEvent event, Long key) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", event.eventId());
        body.put("eventType", event.eventType());
        body.put("schemaVersion", event.schemaVersion());
        body.put("occurredAt", event.occurredAt());
        body.put("producer", event.producer());
        body.put("aggregateId", event.aggregateId());
        body.put("correlationId", event.correlationId());
        body.put("data", event.data());
        sendRaw(event.eventType(), event.eventId(), jsonMapper.writeValueAsString(body), key);
    }

    private RecordMetadata sendRaw(String eventType, UUID eventId, String body) throws Exception {
        return sendRaw(eventType, eventId, body, productId);
    }

    private RecordMetadata sendRaw(String eventType, UUID eventId, String body, Long key) throws Exception {
        var record = new ProducerRecord<>(TOPIC, String.valueOf(key), body);
        record.headers().add("eventId", eventId.toString().getBytes(UTF_8));
        record.headers().add("eventType", eventType.getBytes(UTF_8));
        try (var producer = new KafkaProducer<String, String>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            return producer.send(record).get(10, TimeUnit.SECONDS);
        }
    }

    // DLT를 처음부터 지금 끝까지 읽어 이 테스트의 키만 돌려준다. 다른 테스트가 넣은 DLT 메시지도 있다
    private List<ConsumerRecord<String, String>> deadLetters() {
        var partition = new TopicPartition(DLT, 0);
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));
            var end = consumer.endOffsets(List.of(partition)).get(partition);
            var records = new ArrayList<ConsumerRecord<String, String>>();
            while (consumer.position(partition) < end) {
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
            }
            return records.stream().filter(record -> String.valueOf(productId).equals(record.key())).toList();
        }
    }

    private static Long randomId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
