package com.bbcc.kidly.boundedcontext.search;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearch;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchCondition;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchItem;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchSort;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import com.bbcc.kidly.support.SearchIndexes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicExistsException;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 상품 · 판매자가 Kafka로 보낸 메시지를 검색이 받아 색인하고, 목록 검색에 나오는지 실제 Kafka · Elasticsearch로 본다(#526).
 * 메시지는 보내는 쪽과 같은 모양(키 = 대상 ID, 헤더 eventType · eventId, 본문 = 공통 칸 7개 + data)으로 직접 넣는다.
 * 검색은 상품 · 판매자 토픽을 선언하지 않으므로(보내는 쪽이 선언) 테스트가 만든다.
 */
@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class SearchKafkaIntegrationTest {

    private static final String PRODUCT_TOPIC = "kidly.product.events";
    private static final String SELLER_TOPIC = "kidly.seller.events";
    private static final String PRODUCT_DLT = "kidly.product.events.search.dlt";
    private static final List<String> LISTENER_IDS = List.of("searchProductEventListener", "searchSellerEventListener");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    // 재시도는 1초 간격 3번이라, 재시도했다면 DLT에 들어가기까지 3초 이상 걸린다
    private static final long RETRY_BACKOFF_TOTAL_MS = 3_000;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ProductSearch productSearch;

    @Autowired
    private SearchIndexes indexes;

    private final long productId = ThreadLocalRandom.current().nextLong(1, 1_000_000_000L);
    private final long sellerId = ThreadLocalRandom.current().nextLong(1, 1_000_000_000L);

    private final List<MessageListenerContainer> listeners = new ArrayList<>();

    // 읽기 시작 위치가 latest라 파티션을 배정받기 전에 보낸 메시지는 받지 못한다
    @BeforeEach
    void startListeners() {
        createTopics();
        for (String id : LISTENER_IDS) {
            MessageListenerContainer listener = registry.getListenerContainer(id);
            listener.start();
            listeners.add(listener);
        }
        AsyncWait.until(TIMEOUT, () -> listeners.stream().noneMatch(l -> l.getAssignedPartitions().isEmpty()));
    }

    // stop()은 처리 중인 메시지가 끝날 때까지 기다리므로 뒤이은 색인 비우기와 겹치지 않는다
    @AfterEach
    void stopListeners() {
        listeners.forEach(MessageListenerContainer::stop);
    }

    @Test
    @DisplayName("product.changed를 받아 색인하고, 형태소 검색으로 찾는다")
    void productChanged() throws Exception {
        sendProduct("product.changed", envelope(product("여아 원피스"), "product.changed", "product"));

        assertFound("원피스 여아용", item -> assertThat(item.name()).isEqualTo("여아 원피스"));
    }

    @Test
    @DisplayName("seller.changed를 받아 그 판매자 상품에 상호명을 붙인다")
    void sellerChanged() throws Exception {
        sendProduct("product.changed", envelope(product("여아 원피스"), "product.changed", "product"));
        sendSeller(envelope(SearchEvents.seller(sellerId, "키들리 상점"), "seller.changed", "seller"));

        AsyncWait.until(TIMEOUT, () -> find("원피스").map(item -> "키들리 상점".equals(item.businessName()))
            .orElse(false));
    }

    @Test
    @DisplayName("검색이 받지 않는 종류는 건너뛰고 DLT로 보내지 않으며, 뒤 메시지를 처리한다")
    void skipOtherEventType() throws Exception {
        sendProduct("stock.deducted", "받지 않는 종류라 읽지 않는 본문");
        // 키(상품 ID)가 같아 같은 파티션이므로, 뒤 메시지를 처리했으면 앞 메시지는 이미 처리됐다
        sendProduct("product.changed", envelope(product("여아 원피스"), "product.changed", "product"));

        assertFound("원피스", item -> assertThat(item.productId()).isEqualTo(productId));
        assertThat(deadLetters()).isEmpty();
    }

    @Test
    @DisplayName("본문을 읽지 못하면 재시도 없이 DLT로 보내고, 로그에 본문을 남기지 않는다")
    void deadLetterUnreadableBody(CapturedOutput output) throws Exception {
        String secret = "본문-" + UUID.randomUUID();
        String body = "{\"data\":{\"productId\":\"숫자 아님\",\"name\":\"" + secret + "\"}}";

        RecordMetadata sent = sendProduct("product.changed", body);

        assertDeadLetteredWithoutRetry(sent, body);
        assertThat(output.getOut()).contains("key=" + productId).doesNotContain(secret);
    }

    @Test
    @DisplayName("검색 문서로 만들 수 없는 상품(옵션 없음)은 재시도 없이 DLT로 보낸다")
    void deadLetterProductWithoutOptions() throws Exception {
        ProductChangedEvent noOptions = new ProductChangedEvent(UUID.randomUUID(), SearchEvents.OCCURRED_AT,
            new ProductChangedEvent.Data(productId, sellerId, "옵션 없는 상품", 3L, ProductStatus.SALE_PENDING, null,
                SearchEvents.OCCURRED_AT.minusDays(1), false, List.of()));
        String body = envelope(noOptions, "product.changed", "product");

        RecordMetadata sent = sendProduct("product.changed", body);

        assertDeadLetteredWithoutRetry(sent, body);
    }

    private ProductChangedEvent product(String name) {
        return SearchEvents.product(productId, name, sellerId, 3L, ProductStatus.ON_SALE, 15_000, 3L, 1);
    }

    private void assertFound(String keyword, java.util.function.Consumer<ProductSearchItem> requirement) {
        AsyncWait.until(TIMEOUT, () -> find(keyword).isPresent());
        requirement.accept(find(keyword).orElseThrow());
    }

    // 저장한 문서는 새로 고침 전에는 검색되지 않으므로 찾을 때마다 새로 고친다
    private Optional<ProductSearchItem> find(String keyword) {
        indexes.refresh();
        return productSearch.search(new ProductSearchCondition(null, keyword, Map.of(), ProductSearchSort.NEWEST),
                PageRequest.of(0, 20)).getContent().stream()
            .filter(item -> item.productId() == productId)
            .findFirst();
    }

    private void assertDeadLetteredWithoutRetry(RecordMetadata sent, String body) {
        AsyncWait.until(TIMEOUT, () -> !deadLetters().isEmpty());
        assertThat(deadLetters()).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.value()).isEqualTo(body);
            assertThat(deadLetter.timestamp() - sent.timestamp()).isLessThan(RETRY_BACKOFF_TOTAL_MS);
        });
    }

    // 보내는 쪽 본문과 같은 모양: 공통 칸 7개 + data. 이벤트 record에 없는 칸(eventType 등)도 들어 있다
    private String envelope(DomainEvent event, String eventType, String producer) {
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = jsonMapper.convertValue(event, Map.class);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", fields.get("eventId"));
        body.put("eventType", eventType);
        body.put("schemaVersion", 1);
        body.put("occurredAt", fields.get("occurredAt"));
        body.put("producer", producer);
        body.put("aggregateId", event.aggregateId());
        body.put("correlationId", event.correlationId());
        body.put("data", fields.get("data"));
        return jsonMapper.writeValueAsString(body);
    }

    private RecordMetadata sendProduct(String eventType, String body) throws Exception {
        return send(PRODUCT_TOPIC, String.valueOf(productId), eventType, body);
    }

    private RecordMetadata sendSeller(String body) throws Exception {
        return send(SELLER_TOPIC, String.valueOf(sellerId), "seller.changed", body);
    }

    private RecordMetadata send(String topic, String key, String eventType, String body) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, body);
        record.headers().add("eventId", UUID.randomUUID().toString().getBytes(UTF_8));
        record.headers().add("eventType", eventType.getBytes(UTF_8));
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            return producer.send(record).get(10, TimeUnit.SECONDS);
        }
    }

    // 보내는 쪽과 같게 파티션 3개로 만든다. 이미 있으면 그대로 쓴다
    private void createTopics() {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafka.getBootstrapServers()))) {
            for (String topic : List.of(PRODUCT_TOPIC, SELLER_TOPIC)) {
                try {
                    admin.createTopics(List.of(new NewTopic(topic, 3, (short) 1))).all().get(10, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException e) {
                    if (!(e.getCause() instanceof TopicExistsException)) {
                        throw new IllegalStateException("토픽을 만들지 못했습니다: " + topic, e);
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("토픽을 만들지 못했습니다: " + topic, e);
                }
            }
        }
    }

    // DLT를 처음부터 지금 끝까지 읽어 이 테스트의 키만 돌려준다. 다른 테스트가 넣은 DLT 메시지도 있다
    private List<ConsumerRecord<String, String>> deadLetters() {
        TopicPartition partition = new TopicPartition(PRODUCT_DLT, 0);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));
            long end = consumer.endOffsets(List.of(partition)).get(partition);
            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            while (consumer.position(partition) < end) {
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
            }
            return records.stream().filter(record -> String.valueOf(productId).equals(record.key())).toList();
        }
    }
}
