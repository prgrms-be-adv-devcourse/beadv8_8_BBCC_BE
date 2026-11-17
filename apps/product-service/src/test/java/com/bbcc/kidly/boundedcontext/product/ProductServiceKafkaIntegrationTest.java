package com.bbcc.kidly.boundedcontext.product;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSeller;
import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSellerRepository;
import com.bbcc.kidly.boundedcontext.product.in.outbox.ProductOutboxScheduler;
import com.bbcc.kidly.boundedcontext.product.in.replica.ProductSellerKafkaListener;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

@KafkaIntegrationTest
class ProductServiceKafkaIntegrationTest {

    private static final String SELLER_TOPIC = "kidly.seller.events";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final LocalDateTime CHANGED_AT = LocalDateTime.of(2026, 11, 3, 10, 0);

    @Autowired
    private ProductReplicaSellerRepository replicaSellerRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ProductKafkaListenerTestGuard listenerGuard;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private ApplicationContext context;

    private MessageListenerContainer sellerListener;

    @BeforeEach
    void startListener() {
        sellerListener = ProductKafkaMessages.startListener(listenerGuard, ProductSellerKafkaListener.ID);
    }

    // stop()은 처리 중인 메시지가 끝날 때까지 기다리므로 뒤이은 테이블 비우기와 겹치지 않는다
    @AfterEach
    void stopListener() {
        sellerListener.stop();
    }

    @Test
    @DisplayName("판매자 정보 변경됨을 상품 그룹으로 받아 판매자 복사본에 저장한다")
    void saveSellerReplica() throws Exception {
        Long sellerId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);

        send(new SellerChangedEvent(UUID.randomUUID(), CHANGED_AT,
            new SellerChangedEvent.Data(sellerId, "소소한옷장", SellerStatus.ACTIVE)));

        AsyncWait.until(TIMEOUT, () -> replicaSellerRepository.findById(sellerId).isPresent());
        assertThat(replicaSellerRepository.findById(sellerId)).get()
            .extracting(ProductReplicaSeller::getBusinessName, ProductReplicaSeller::getStatus)
            .containsExactly("소소한옷장", SellerStatus.ACTIVE);
    }

    @Test
    @DisplayName("Kafka 보내기가 켜진 상품 서비스는 아웃박스 릴레이 스케줄러를 만든다")
    void outboxRelayEnabled() {
        assertThat(context.getBeansOfType(ProductOutboxScheduler.class)).hasSize(1);
    }

    private void send(SellerChangedEvent event) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", event.eventId());
        body.put("eventType", event.eventType());
        body.put("schemaVersion", event.schemaVersion());
        body.put("occurredAt", event.occurredAt());
        body.put("producer", event.producer());
        body.put("aggregateId", event.aggregateId());
        body.put("correlationId", event.correlationId());
        body.put("data", event.data());
        var record = new ProducerRecord<>(SELLER_TOPIC, event.correlationId(), jsonMapper.writeValueAsString(body));
        record.headers().add("eventType", event.eventType().getBytes(UTF_8));
        record.headers().add("eventId", event.eventId().toString().getBytes(UTF_8));
        try (var producer = new KafkaProducer<String, String>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(record).get(10, TimeUnit.SECONDS);
        }
    }
}
