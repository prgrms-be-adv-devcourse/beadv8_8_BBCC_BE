package com.bbcc.kidly.boundedcontext.seller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.MemberFixture;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.seller.app.SellerApplicationService;
import com.bbcc.kidly.boundedcontext.seller.app.SellerService;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationRequest;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerSuspendRequest;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.time.Duration;
import java.time.LocalDateTime;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 판매자 이벤트가 커밋 뒤 kidly.seller.events에 규칙대로(키 · 헤더 · 본문) 도착하는지 본다(#548).
 * 받는 쪽(회원 · 주문 · 상품 · 추천)이 Spring 이벤트로 받는 것은 기존 테스트가 본다.
 * 다른 테스트의 판매자 이벤트도 같은 토픽에 쌓이므로 헤더 eventId로 이 테스트의 메시지를 찾는다.
 */
@KafkaIntegrationTest
@RecordApplicationEvents
class SellerKafkaPublishIntegrationTest {

    private static final String TOPIC = "kidly.seller.events";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private SellerApplicationService sellerApplicationService;

    @Autowired
    private SellerService sellerService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private KafkaContainer kafka;

    private KafkaConsumer<String, String> consumer;
    private final List<ConsumerRecord<String, String>> received = new ArrayList<>();

    @BeforeEach
    void subscribe() {
        consumer = new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "seller-kafka-test-" + UUID.randomUUID(),
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
    @DisplayName("승인하면 판매자 ID 키로 보내고 헤더 eventType · eventId와 본문 8칸을 채운다")
    void approved() {
        var sellerId = approveSeller();

        var changed = events.stream(SellerChangedEvent.class).findFirst().orElseThrow();
        var record = awaitRecord(changed);
        assertThat(record.key()).isEqualTo(String.valueOf(sellerId));
        assertThat(header(record, "eventType")).isEqualTo("seller.changed");
        assertThat(record.headers().lastHeader("__TypeId__")).isNull();
        assertThat(JSON_MAPPER.readTree(record.value()).propertyNames()).containsExactly("eventId", "eventType",
            "schemaVersion", "occurredAt", "producer", "aggregateId", "correlationId", "data");
    }

    @Test
    @DisplayName("정지하면 seller.changed(SUSPENDED)를 판매자 ID 키로 보낸다")
    void suspended() {
        var sellerId = approveSeller();

        sellerService.suspend(sellerId, SellerFixture.ADMIN_ID, new SellerSuspendRequest("허위 상품 등록 신고"));

        var suspended = events.stream(SellerChangedEvent.class)
            .filter(event -> event.data().status() == SellerStatus.SUSPENDED)
            .findFirst().orElseThrow();
        assertSent(suspended, "seller.changed", sellerId);
        assertThat(JSON_MAPPER.readTree(awaitRecord(suspended).value()).get("data").get("status").asString())
            .isEqualTo("SUSPENDED");
    }

    @Test
    @DisplayName("롤백되면 보내지 않는다")
    void rolledBack() {
        var sellerId = approveSeller();
        var rolledBack = new SellerChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new SellerChangedEvent.Data(sellerId, "롤백상호", SellerStatus.ACTIVE));
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(rolledBack);
            status.setRollbackOnly();
        });
        sellerService.suspend(sellerId, SellerFixture.ADMIN_ID, new SellerSuspendRequest("허위 상품 등록 신고"));

        // 키(판매자 ID)가 같아 같은 파티션이므로, 뒤에 보낸 것을 받았으면 앞에서 보낸 것은 이미 왔어야 한다
        var committed = events.stream(SellerChangedEvent.class)
            .filter(event -> event.data().status() == SellerStatus.SUSPENDED)
            .filter(event -> !event.eventId().equals(rolledBack.eventId()))
            .findFirst().orElseThrow();
        awaitRecord(committed);
        assertThat(findRecord(rolledBack)).isEmpty();
    }

    // 트랜잭션 없이 호출해야 커밋 뒤 Kafka 전송이 돈다
    private Long approveSeller() {
        var member = memberRepository.save(MemberFixture.kakaoMember());
        var request = new SellerApplicationRequest(SellerFixture.BUSINESS_NAME, SellerFixture.REPRESENTATIVE_NAME,
            SellerFixture.BUSINESS_REGISTRATION_NUMBER);
        var application = sellerApplicationService.submit(member.getId(), member.getNickname(), request);
        sellerApplicationService.approve(application.id(), SellerFixture.ADMIN_ID);
        return member.getId();
    }

    private void assertSent(DomainEvent event, String eventType, Long key) {
        var record = awaitRecord(event);
        assertThat(record.key()).isEqualTo(String.valueOf(key));
        assertThat(header(record, "eventType")).isEqualTo(eventType);
    }

    // 전송은 비동기라 메시지가 올 때까지 읽는다. 받은 뒤 끝내므로 다음 테스트에 끼어들지 않는다
    private ConsumerRecord<String, String> awaitRecord(DomainEvent event) {
        AsyncWait.until(TIMEOUT, () -> {
            consumer.poll(Duration.ofMillis(200)).forEach(received::add);
            return findRecord(event).isPresent();
        });
        return findRecord(event).orElseThrow();
    }

    private Optional<ConsumerRecord<String, String>> findRecord(DomainEvent event) {
        return received.stream()
            .filter(record -> event.eventId().toString().equals(header(record, "eventId")))
            .findFirst();
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), UTF_8);
    }
}
