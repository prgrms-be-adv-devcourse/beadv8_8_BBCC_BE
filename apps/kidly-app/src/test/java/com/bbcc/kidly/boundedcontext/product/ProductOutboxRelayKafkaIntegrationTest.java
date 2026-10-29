package com.bbcc.kidly.boundedcontext.product;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.app.outbox.ProductOutboxRelayService;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.domain.StockDeductionFailureCode;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductionFailedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 상품 이벤트가 커밋 직전 아웃박스에 저장된 뒤 릴레이를 거쳐 kidly.product.events에 규칙대로 도착하는지 실제 DB와 브로커로 본다.
 * 테스트 설정에서 릴레이 주기를 길게 두었으므로 릴레이는 테스트가 직접 부른다.
 * 다른 테스트의 메시지도 같은 토픽에 쌓이므로 헤더 eventId로 이 테스트의 메시지를 찾는다.
 */
@KafkaIntegrationTest
class ProductOutboxRelayKafkaIntegrationTest {

    private static final String TOPIC = "kidly.product.events";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 30, 12, 0);

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ProductOutboxRelayService relayService;

    @Autowired
    private ProductOutboxRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KafkaContainer kafka;

    private KafkaConsumer<String, String> consumer;
    private final List<ConsumerRecord<String, String>> received = new ArrayList<>();

    @BeforeEach
    void subscribe() {
        consumer = new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "product-outbox-relay-test-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumer.subscribe(List.of(TOPIC));
    }

    @AfterEach
    void close() {
        consumer.close();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("productEvents")
    @DisplayName("상품 이벤트를 발행해 커밋하고 릴레이를 부르면 키 · 헤더 2개 · 본문(공통 칸 7개 + data)으로 도착하고 보낸 시각이 남는다")
    void relayCommittedEventToTopic(DomainEvent event, String eventType, String key) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        relayService.relay();

        ConsumerRecord<String, String> record = awaitRecord(event.eventId());
        assertThat(record.key()).isEqualTo(key);
        assertThat(record.headers().toArray()).extracting(Header::key).containsExactly("eventType", "eventId");
        assertThat(header(record, "eventType")).isEqualTo(eventType);
        assertThat(JSON_MAPPER.readTree(record.value()).propertyNames()).containsExactly("eventId", "eventType",
            "schemaVersion", "occurredAt", "producer", "aggregateId", "correlationId", "data");
        assertThat(outboxRow(event.eventId())).containsEntry("attempt_count", 1)
            .extractingByKey("sent_at").isNotNull();
    }

    @Test
    @DisplayName("보내다 실패하면 그 메시지에 실패를 남기고 뒤 메시지는 보내지 않으며, 다음 실행은 실패한 메시지부터 다시 보낸다")
    void stopAtFailureAndRetryFromSameMessage() {
        // 브로커의 토픽 자동 생성이 꺼져 있어 선언하지 않은 토픽으로는 보내지 못한다
        UUID failingId = UUID.randomUUID();
        UUID afterId = UUID.randomUUID();
        outboxRepository.save(ProductOutboxMessage.create(new ProductOutboxMessage.CreateInfo(
            failingId, "product.changed", "kidly.product.missing-" + failingId, "1",
            ProductOutboxMessageFixture.PAYLOAD)));
        outboxRepository.save(ProductOutboxMessage.create(ProductOutboxMessageFixture.productChanged(afterId)));

        relayService.relay();

        assertThat(outboxRow(failingId)).containsEntry("attempt_count", 1)
            .containsEntry("sent_at", null).extractingByKey("last_error").isNotNull();
        assertThat(outboxRow(afterId)).containsEntry("attempt_count", 0).containsEntry("sent_at", null);

        relayService.relay();

        assertThat(outboxRow(failingId)).containsEntry("attempt_count", 2).containsEntry("sent_at", null);
        assertThat(outboxRow(afterId)).containsEntry("attempt_count", 0).containsEntry("sent_at", null);
    }

    static Stream<Arguments> productEvents() {
        return Stream.of(
            Arguments.of(Named.of("상품 정보 변경됨", new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT,
                new ProductChangedEvent.Data(1L, 2L, "데일리 맨투맨", 3L, ProductStatus.ON_SALE, null, OCCURRED_AT,
                    false, List.of()))), "product.changed", "1"),
            Arguments.of(Named.of("재고 차감됨", new StockDeductedEvent(UUID.randomUUID(), OCCURRED_AT,
                new StockDeductedEvent.Data(77L))), "stock.deducted", "77"),
            Arguments.of(Named.of("재고 차감 실패", new StockDeductionFailedEvent(UUID.randomUUID(), OCCURRED_AT,
                new StockDeductionFailedEvent.Data(78L, StockDeductionFailureCode.OUT_OF_STOCK, List.of(
                    new StockDeductionFailedEvent.Failure(781L, StockDeductionFailureCode.OUT_OF_STOCK))))),
                "stock.deduction-failed", "78"));
    }

    private Map<String, Object> outboxRow(UUID eventId) {
        return jdbcTemplate.queryForMap(
            "select attempt_count, sent_at, last_error from product.product_outbox where event_id = ?", eventId);
    }

    private ConsumerRecord<String, String> awaitRecord(UUID eventId) {
        AsyncWait.until(TIMEOUT, () -> {
            consumer.poll(Duration.ofMillis(200)).forEach(received::add);
            return findRecord(eventId).isPresent();
        });
        return findRecord(eventId).orElseThrow();
    }

    private Optional<ConsumerRecord<String, String>> findRecord(UUID eventId) {
        return received.stream()
            .filter(record -> eventId.toString().equals(header(record, "eventId")))
            .findFirst();
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), UTF_8);
    }
}
