package com.bbcc.kidly.boundedcontext.notification;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.app.MemberContactService;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationDeadLetterService;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetters.State;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetters.Summary;
import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import com.bbcc.kidly.boundedcontext.notification.in.event.NotificationDeadLetterKafkaListener;
import com.bbcc.kidly.boundedcontext.notification.out.JpaNotificationRepository;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.time.Duration;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 처리하지 못한 판매자 승인이 DLT를 거쳐 DB에 보관되고, 관리자가 원인을 고친 뒤 재처리하면 승인 메일이 만들어지는지 실제 Kafka로 본다.
 * 회원 이메일 복사본이 없으면 승인 처리가 실패한다(SellerStatusService). 판매자 리스너와 DLT 보관 리스너만 켠다.
 * DLT에는 앞 테스트가 남긴 메시지도 있으므로 이 테스트의 eventId로 고른다.
 */
@KafkaIntegrationTest
class NotificationDeadLetterKafkaIntegrationTest {

    private static final String SELLER_TOPIC = "kidly.seller.events";
    private static final String SELLER_DLT = "kidly.seller.events.notification.dlt";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final long ADMIN_ID = 99L;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JpaNotificationRepository jpaNotificationRepository;

    @Autowired
    private MemberContactService memberContactService;

    @Autowired
    private FakeNotificationEmailSender fakeNotificationEmailSender;

    @Autowired
    private NotificationDeadLetterService deadLetterService;

    private final long sellerId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    private final String sellerEmail = "seller-" + UUID.randomUUID() + "@kidly.co.kr";

    private List<MessageListenerContainer> listeners;

    @BeforeEach
    void startListeners() {
        listeners = List.of(kafkaListenerEndpointRegistry.getListenerContainer("notificationSellerEventListener"),
            kafkaListenerEndpointRegistry.getListenerContainer(NotificationDeadLetterKafkaListener.ID));
        listeners.forEach(MessageListenerContainer::start);
        AsyncWait.until(TIMEOUT, () -> listeners.stream().noneMatch(l -> l.getAssignedPartitions().isEmpty()));
    }

    @AfterEach
    void stopListeners() {
        listeners.forEach(MessageListenerContainer::stop);
        fakeNotificationEmailSender.reset();
    }

    @Test
    @DisplayName("이메일 복사본 없는 판매자 승인 → DLT DB 보관 → 그대로 재처리하면 실패 기록 → 복사본이 생긴 뒤 재처리하면 승인 메일")
    void storesAndReprocessesDeadLetter() throws Exception {
        var event = new SellerChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new SellerChangedEvent.Data(sellerId, "키들리 상점", SellerStatus.ACTIVE));
        String eventId = event.eventId().toString();
        RecordMetadata sent = send(event);

        AsyncWait.until(TIMEOUT, () -> deadLetterCount(eventId) == 1);
        Long id = deadLetterId(eventId);
        assertThat(deadLetterService.list(null, 0, 100).getContent()).extracting(Summary::getId).contains(id);
        Summary stored = deadLetterService.get(id);
        assertThat(stored.getState()).isEqualTo(State.OPEN);
        assertThat(stored.getDltTopic()).isEqualTo(SELLER_DLT);
        assertThat(stored.getSourceTopic()).isEqualTo(SELLER_TOPIC);
        assertThat(stored.getSourcePartition()).isEqualTo(sent.partition());
        assertThat(stored.getSourceOffset()).isEqualTo(sent.offset());
        assertThat(stored.getMessageKey()).isEqualTo(event.correlationId());
        assertThat(stored.getEventType()).isEqualTo("seller.changed");
        assertThat(stored.getFailureType()).isEqualTo(IllegalStateException.class.getName());

        Summary failed = deadLetterService.reprocess(id, ADMIN_ID);
        assertThat(failed.getState()).isEqualTo(State.OPEN);
        assertThat(failed.getReplayCount()).isOne();
        assertThat(failed.getLastReplayedBy()).isEqualTo(ADMIN_ID);
        assertThat(failed.getLastReplayFailureType()).isEqualTo(IllegalStateException.class.getName());
        assertThat(notifications()).isEmpty();

        memberContactService.save(sellerId, sellerEmail);
        Summary reprocessed = deadLetterService.reprocess(id, ADMIN_ID);
        assertThat(reprocessed.getState()).isEqualTo(State.REPROCESSED);
        assertThat(reprocessed.getReplayCount()).isEqualTo(2);
        assertThat(reprocessed.getLastReplayFailureType()).isNull();
        assertThat(notifications()).singleElement()
            .satisfies(n -> assertThat(n.getType()).isEqualTo(NotificationType.SELLER_APPROVED));
        assertThat(deadLetterService.list("REPROCESSED", 0, 100).getContent()).extracting(Summary::getId)
            .contains(id);

        // 이미 성공한 기록은 다시 실행하지 않는다
        assertThat(deadLetterService.reprocess(id, ADMIN_ID).getReplayCount()).isEqualTo(2);
        assertThat(notifications()).hasSize(1);
    }

    private List<Notification> notifications() {
        return jpaNotificationRepository.findAll().stream()
            .filter(notification -> sellerEmail.equals(notification.getRecipient()))
            .toList();
    }

    // 판매자가 보내는 형식 그대로 넣는다: 공통 칸 7개 + data
    private RecordMetadata send(DomainEvent event) throws Exception {
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
        record.headers().add("eventId", event.eventId().toString().getBytes(UTF_8));
        record.headers().add("eventType", event.eventType().getBytes(UTF_8));
        try (var producer = new KafkaProducer<String, String>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            return producer.send(record).get(10, TimeUnit.SECONDS);
        }
    }

    private long deadLetterCount(String eventId) {
        return jdbcTemplate.queryForObject("select count(*) from notification.dead_letter where event_id = ?",
            Long.class, eventId);
    }

    private Long deadLetterId(String eventId) {
        return jdbcTemplate.queryForObject("select id from notification.dead_letter where event_id = ?",
            Long.class, eventId);
    }
}
