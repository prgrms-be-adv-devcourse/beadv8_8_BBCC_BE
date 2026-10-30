package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productOption;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSeller;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSellerRepository;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRange;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 추천이 판매자 · 자녀 · 상품 이벤트를 Kafka로 받아 복사본에 반영하는지 확인한다(#576).
 * 메시지는 보내는 도메인을 거치지 않고 팀 Kafka 규칙의 형식(키, 헤더 두 개, 본문 공통 칸 7개 + data)으로 토픽에 직접 넣는다.
 * 보내는 도메인을 거치면 아직 남아 있는 Spring 이벤트 리스너가 먼저 반영해서, Kafka로 받았는지 구분할 수 없기 때문이다.
 * 같은 토픽에는 다른 테스트의 메시지도 쌓여 이어서 받을 수 있으므로, 판매자 · 자녀 · 상품 ID를 테스트마다 새로 만든다.
 */
@KafkaIntegrationTest
class RecommendKafkaListenerIntegrationTest {

    private static final String SELLER_TOPIC = "kidly.seller.events";
    private static final String CHILD_TOPIC = "kidly.childprofile.events";
    private static final String PRODUCT_TOPIC = "kidly.product.events";
    private static final String GROUP = "kidly.recommend";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final LocalDateTime CHANGED_AT = LocalDateTime.of(2026, 10, 30, 10, 0);

    @Autowired
    private RecommendReplicaSellerRepository replicaSellerRepository;

    @Autowired
    private RecommendReplicaChildRepository replicaChildRepository;

    @Autowired
    private RecommendReplicaProductRepository replicaProductRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private KafkaContainer kafka;

    private List<MessageListenerContainer> listeners;

    // 리스너는 이 테스트 안에서만 켠다. 읽기 시작 위치가 latest라 파티션을 배정받기 전에 보낸 메시지는 받지 못한다
    @BeforeEach
    void startListeners() {
        listeners = kafkaListenerEndpointRegistry.getAllListenerContainers().stream()
            .filter(container -> GROUP.equals(container.getGroupId()))
            .toList();
        listeners.forEach(MessageListenerContainer::start);
        AsyncWait.until(TIMEOUT, () -> listeners.stream()
            .allMatch(container -> !container.getAssignedPartitions().isEmpty()));
    }

    // stop()은 처리 중인 메시지가 끝날 때까지 기다리므로 뒤이은 테이블 비우기와 겹치지 않는다
    @AfterEach
    void stopListeners() {
        listeners.forEach(MessageListenerContainer::stop);
    }

    @Test
    @DisplayName("판매자 정보 변경됨을 Kafka로 받아 판매자 복사본에 저장한다")
    void saveSellerReplica() throws Exception {
        Long sellerId = newId();

        send(SELLER_TOPIC, new SellerChangedEvent(UUID.randomUUID(), CHANGED_AT,
            new SellerChangedEvent.Data(sellerId, "소소한옷장", SellerStatus.ACTIVE)));

        AsyncWait.until(TIMEOUT, () -> replicaSellerRepository.findById(sellerId).isPresent());
        assertThat(replicaSellerRepository.findById(sellerId)).get()
            .extracting(
                RecommendReplicaSeller::getBusinessName,
                RecommendReplicaSeller::getStatus,
                RecommendReplicaSeller::getEventOccurredAt)
            .containsExactly("소소한옷장", SellerStatus.ACTIVE, CHANGED_AT);
    }

    @Test
    @DisplayName("자녀 정보 변경됨을 Kafka로 받아 자녀 복사본에 저장한다")
    void saveChildReplica() throws Exception {
        Long childId = newId();
        Long memberId = newId();

        send(CHILD_TOPIC, new ChildChangedEvent(UUID.randomUUID(), CHANGED_AT,
            new ChildChangedEvent.Data(childId, memberId, new BigDecimal("115.0"), new BigDecimal("20.0"),
                Gender.MALE, LocalDate.of(2020, 5, 1), BodyType.NORMAL, PreferredFit.LOOSE, false)));

        AsyncWait.until(TIMEOUT, () -> replicaChildRepository.findById(childId).isPresent());
        RecommendReplicaChild replica = replicaChildRepository.findById(childId).orElseThrow();
        assertThat(replica.getHeightCm()).isEqualByComparingTo("115.0");
        assertThat(replica.getWeightKg()).isEqualByComparingTo("20.0");
        assertThat(replica)
            .extracting(
                RecommendReplicaChild::getMemberId,
                RecommendReplicaChild::getGender,
                RecommendReplicaChild::getBirthDate,
                RecommendReplicaChild::getBodyType,
                RecommendReplicaChild::getPreferredFit,
                RecommendReplicaChild::getEventOccurredAt,
                RecommendReplicaChild::isDeleted)
            .containsExactly(memberId, Gender.MALE, LocalDate.of(2020, 5, 1), BodyType.NORMAL, PreferredFit.LOOSE,
                CHANGED_AT, false);
    }

    @Test
    @DisplayName("상품 정보 변경됨을 Kafka로 받아 상품 복사본에 저장한다")
    void saveProductReplica() throws Exception {
        Long productId = newId();
        Long sellerId = newId();

        send(PRODUCT_TOPIC, new ProductChangedEvent(UUID.randomUUID(), CHANGED_AT, new ProductChangedEvent.Data(
            productId, sellerId, "데일리 맨투맨", 3L, ProductStatus.ON_SALE, "https://img.kidly.dev/a.jpg",
            CHANGED_AT.minusDays(1), false,
            List.of(productOption(1L, "110", 105, 115), productOption(2L, "120", 115, 125)))));

        AsyncWait.until(TIMEOUT, () -> replicaProductRepository.findById(productId).isPresent());
        assertThat(replicaProductRepository.findById(productId)).get()
            .extracting(
                RecommendReplicaProduct::getSellerId,
                RecommendReplicaProduct::getName,
                RecommendReplicaProduct::getStatus,
                RecommendReplicaProduct::getMinPrice,
                RecommendReplicaProduct::getEventOccurredAt)
            .containsExactly(sellerId, "데일리 맨투맨", ProductStatus.ON_SALE, 25_000L, CHANGED_AT);
        // 옵션 목록까지 본문에서 읽었는지 사이즈 줄로 확인한다. 사이즈 줄은 지연 로딩이라 트랜잭션 안에서 읽는다
        List<SizeRange> sizes = transactionTemplate.execute(status ->
            replicaProductRepository.findById(productId).orElseThrow().toProductSizes().sizeRanges());
        assertThat(sizes).containsExactlyInAnyOrder(SizeRange.of("110", 105, 115), SizeRange.of("120", 115, 125));
    }

    private static Long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    // 보내는 도메인과 같은 형식으로 넣는다. 키는 correlationId, 헤더는 eventType · eventId, 본문은 공통 칸 7개 + data다
    private RecordMetadata send(String topic, DomainEvent event) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", event.eventId());
        body.put("eventType", event.eventType());
        body.put("schemaVersion", event.schemaVersion());
        body.put("occurredAt", event.occurredAt());
        body.put("producer", event.producer());
        body.put("aggregateId", event.aggregateId());
        body.put("correlationId", event.correlationId());
        body.put("data", event.data());
        return sendRaw(topic, event.correlationId(), event.eventType(), event.eventId(),
            jsonMapper.writeValueAsString(body));
    }

    private RecordMetadata sendRaw(String topic, String key, String eventType, UUID eventId, String body)
        throws Exception {
        var record = new ProducerRecord<>(topic, key, body);
        record.headers().add("eventType", eventType.getBytes(UTF_8));
        record.headers().add("eventId", eventId.toString().getBytes(UTF_8));
        try (var producer = new KafkaProducer<String, String>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            return producer.send(record).get(10, TimeUnit.SECONDS);
        }
    }
}
