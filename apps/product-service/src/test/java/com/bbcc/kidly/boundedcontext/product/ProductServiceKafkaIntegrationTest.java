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

/**
 * 상품 서비스가 구독 그룹(kidly.product)으로 Kafka에 붙어 받은 메시지를 반영하는지 본다(#791).
 * 받는 종류마다의 처리는 상품 수신 테스트(ProductKafkaReceiveIntegrationTest 등)가 확인하고, 여기서는 서비스가 Kafka에 붙는지만 판매자 메시지 하나로 본다.
 * kidly-app이 상품을 가진 동안 이 서비스는 아웃박스 릴레이를 돌리지 않는다(kidly-app만 보낸다).
 */
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

    // 리스너는 ProductKafkaListenerTestGuard가 레지스트리에서 빼 들고 있어 이 테스트 안에서만 켠다.
    // 읽기 시작 위치가 latest라 파티션을 배정받은 뒤에 보내야 받는다
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
        // 같은 토픽에 다른 테스트의 메시지가 쌓여 있을 수 있으므로 판매자 ID를 새로 만든다
        Long sellerId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);

        send(new SellerChangedEvent(UUID.randomUUID(), CHANGED_AT,
            new SellerChangedEvent.Data(sellerId, "소소한옷장", SellerStatus.ACTIVE)));

        AsyncWait.until(TIMEOUT, () -> replicaSellerRepository.findById(sellerId).isPresent());
        assertThat(replicaSellerRepository.findById(sellerId)).get()
            .extracting(ProductReplicaSeller::getBusinessName, ProductReplicaSeller::getStatus)
            .containsExactly("소소한옷장", SellerStatus.ACTIVE);
    }

    // Kafka 보내기가 켜진 컨텍스트에서도 릴레이 스위치가 꺼져 있어 스케줄러가 없다
    @Test
    @DisplayName("kidly-app이 상품을 가진 동안 상품 서비스는 아웃박스 릴레이 스케줄러를 만들지 않는다")
    void noOutboxRelay() {
        assertThat(context.getBeanNamesForType(ProductOutboxScheduler.class)).isEmpty();
    }

    // 회원 서비스가 보내는 형식 그대로다: 키는 correlationId, 헤더 eventType · eventId, 본문은 공통 칸 7개 + data
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
