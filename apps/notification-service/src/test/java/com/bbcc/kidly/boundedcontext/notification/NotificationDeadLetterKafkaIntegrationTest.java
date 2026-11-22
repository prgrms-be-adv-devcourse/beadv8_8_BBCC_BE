package com.bbcc.kidly.boundedcontext.notification;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.app.MemberContactService;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationDeadLetterService;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetters.Received;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetters.State;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetters.Summary;
import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import com.bbcc.kidly.boundedcontext.notification.in.event.NotificationDeadLetterKafkaListener;
import com.bbcc.kidly.boundedcontext.notification.out.JpaNotificationRepository;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.time.Duration;
import java.time.LocalDateTime;
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
 * 본문을 읽을 수 없는 판매자 메시지는 재시도 없이 DLT로 가고, DLT 보관 리스너가 DB에 남긴다. 판매자 리스너와 DLT 보관 리스너만 켠다.
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
    @DisplayName("본문을 읽을 수 없는 판매자 메시지 → DLT DB 보관 → 그대로 재처리하면 실패를 기록하고 OPEN으로 남는다")
    void storesUnreadableMessageAndRecordsFailedReprocess() throws Exception {
        String eventId = UUID.randomUUID().toString();
        RecordMetadata sent = send(eventId, "seller.changed", "{not-json");

        AsyncWait.until(TIMEOUT, () -> deadLetterCount(eventId) == 1);
        Long id = deadLetterId(eventId);
        assertThat(deadLetterService.list(null, 0, 100).getContent()).extracting(Summary::getId).contains(id);
        Summary stored = deadLetterService.get(id);
        assertThat(stored.getState()).isEqualTo(State.OPEN);
        assertThat(stored.getDltTopic()).isEqualTo(SELLER_DLT);
        assertThat(stored.getSourceTopic()).isEqualTo(SELLER_TOPIC);
        assertThat(stored.getSourcePartition()).isEqualTo(sent.partition());
        assertThat(stored.getSourceOffset()).isEqualTo(sent.offset());
        assertThat(stored.getEventType()).isEqualTo("seller.changed");
        assertThat(stored.getFailureType()).isNotNull();

        Summary failed = deadLetterService.reprocess(id, ADMIN_ID);
        assertThat(failed.getState()).isEqualTo(State.OPEN);
        assertThat(failed.getReplayCount()).isOne();
        assertThat(failed.getLastReplayedBy()).isEqualTo(ADMIN_ID);
        assertThat(failed.getLastReplayFailureType()).isNotNull();
    }

    @Test
    @DisplayName("보관된 판매자 승인을 재처리하면 승인 메일과 알림함 알림이 한 번 생기고, 다시 재처리해도 늘지 않는다")
    void reprocessesStoredSellerApprovalOnce() throws Exception {
        memberContactService.save(sellerId, sellerEmail);
        var event = new SellerChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new SellerChangedEvent.Data(sellerId, "키들리 상점", SellerStatus.ACTIVE));
        long dltOffset = ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);
        deadLetterService.receive(new Received(SELLER_DLT, 0, dltOffset, SELLER_TOPIC, 0, dltOffset,
            event.correlationId(), event.eventType(), event.eventId().toString(),
            jsonMapper.writeValueAsString(EventEnvelope.of(event)), IllegalStateException.class.getName()));
        Long id = deadLetterId(event.eventId().toString());

        Summary reprocessed = deadLetterService.reprocess(id, ADMIN_ID);
        assertThat(reprocessed.getState()).isEqualTo(State.REPROCESSED);
        assertThat(reprocessed.getReplayCount()).isOne();
        assertThat(reprocessed.getLastReplayFailureType()).isNull();
        assertThat(notifications()).singleElement()
            .satisfies(n -> assertThat(n.getType()).isEqualTo(NotificationType.SELLER_APPROVED));
        assertThat(inboxCount()).isOne();

        assertThat(deadLetterService.reprocess(id, ADMIN_ID).getReplayCount()).isOne();
        assertThat(notifications()).hasSize(1);
        assertThat(inboxCount()).isOne();
    }

    private List<Notification> notifications() {
        return jpaNotificationRepository.findAll().stream()
            .filter(notification -> sellerEmail.equals(notification.getRecipient()))
            .toList();
    }

    private RecordMetadata send(String eventId, String eventType, String body) throws Exception {
        var record = new ProducerRecord<>(SELLER_TOPIC, String.valueOf(sellerId), body);
        record.headers().add("eventId", eventId.getBytes(UTF_8));
        record.headers().add("eventType", eventType.getBytes(UTF_8));
        try (var producer = new KafkaProducer<String, String>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            return producer.send(record).get(10, TimeUnit.SECONDS);
        }
    }

    private long inboxCount() {
        return jdbcTemplate.queryForObject("select count(*) from notification.inbox where recipient_id = ?",
            Long.class, sellerId);
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
