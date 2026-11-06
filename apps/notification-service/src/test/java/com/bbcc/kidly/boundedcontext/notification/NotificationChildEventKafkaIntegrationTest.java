package com.bbcc.kidly.boundedcontext.notification;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.domain.ChildAccountCopyRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotification;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipientType;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import com.bbcc.kidly.boundedcontext.notification.out.JpaInboxNotificationRepository;
import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.order.event.WishRejectedEvent;
import com.bbcc.kidly.shared.order.event.WishSentEvent;
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
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회원(인증)이 kidly.auth.events에 보낸 자녀 계정 생성 · 삭제로 자녀 계정 복사본을 두고,
 * 주문이 kidly.order.events에 보낸 조르기 거절을 그 자녀 계정의 알림함에 넣는다.
 * 보내는 쪽 형식(키 correlationId, 헤더 eventType · eventId, 본문 공통 칸 + data)으로 토픽에 직접 넣는다.
 * 자녀 계정 · 프로필 · 조르기 ID를 테스트마다 새로 만들고, 결과는 그 자녀 계정의 알림함으로 걸러 본다.
 */
@KafkaIntegrationTest
class NotificationChildEventKafkaIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JpaInboxNotificationRepository jpaInboxNotificationRepository;

    @Autowired
    private ChildAccountCopyRepository childAccountCopyRepository;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private KafkaContainer kafka;

    private final Long accountId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    private final Long childProfileId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    private final Long wishRequestId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);

    private MessageListenerContainer authListener;
    private MessageListenerContainer orderListener;

    @BeforeEach
    void startListeners() {
        authListener = kafkaListenerEndpointRegistry.getListenerContainer("notificationAuthEventListener");
        orderListener = kafkaListenerEndpointRegistry.getListenerContainer("notificationOrderEventListener");
        authListener.start();
        orderListener.start();
        AsyncWait.until(TIMEOUT, () -> !authListener.getAssignedPartitions().isEmpty()
            && !orderListener.getAssignedPartitions().isEmpty());
    }

    @AfterEach
    void stopListeners() {
        authListener.stop();
        orderListener.stop();
    }

    @Test
    @DisplayName("인증 토픽은 책갈피가 없으므로 처음부터 읽어, 구독 전에 만든 자녀 계정을 놓치지 않는다")
    void readFromEarliest() {
        assertThat(authListener.getContainerProperties().getKafkaConsumerProperties()
            .getProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)).isEqualTo("earliest");
    }

    @Test
    @DisplayName("조르기 거절은 자녀 계정 알림함에 한 건 넣고, 계정이 지워지면 그 알림도 지운다")
    void addWishRejectedAndDeleteWithAccount() throws Exception {
        send("kidly.auth.events", accountChanged(false));
        AsyncWait.until(TIMEOUT, () -> childAccountCopyRepository.findById(accountId).isPresent());
        var rejected = new WishRejectedEvent(UUID.randomUUID(), LocalDateTime.now(), new WishRejectedEvent.Data(
            wishRequestId, childProfileId, 1L, List.of(new WishRejectedEvent.Item(null, 5L, 1)), "ALREADY_HAVE"));

        send("kidly.order.events", rejected);
        send("kidly.order.events", rejected);

        AsyncWait.until(TIMEOUT, () -> !childInbox().isEmpty());
        assertThat(childInbox()).singleElement().satisfies(notification -> {
            assertThat(notification.getType()).isEqualTo(NotificationType.WISH_REJECTED);
            assertThat(notification.getBody()).isEqualTo("비슷한 물건을 이미 가지고 있대요. 조른 상품 1개");
            assertThat(notification.getLinkPath()).isEqualTo("/child/wishes/" + wishRequestId);
        });

        send("kidly.auth.events", accountChanged(true));

        AsyncWait.until(TIMEOUT, () -> childInbox().isEmpty());
        assertThat(childAccountCopyRepository.findById(accountId)).get()
            .satisfies(copy -> assertThat(copy.isDeleted()).isTrue());
    }

    @Test
    @DisplayName("자녀 프로필에 활성 계정이 없으면 거절을 받아도 자녀 알림을 넣지 않는다")
    void skipWithoutAccount() throws Exception {
        send("kidly.order.events", new WishRejectedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new WishRejectedEvent.Data(wishRequestId, childProfileId, 1L, List.of(), "OTHER")));
        // 같은 키(자녀 프로필 ID)라 같은 파티션이다. 뒤 메시지가 처리됐으면 앞 메시지도 처리됐다
        Long guardianId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        send("kidly.order.events", new WishSentEvent(UUID.randomUUID(), LocalDateTime.now(),
            new WishSentEvent.Data(wishRequestId, childProfileId, guardianId, List.of())));

        AsyncWait.until(TIMEOUT, () -> jpaInboxNotificationRepository.findAll().stream()
            .anyMatch(notification -> guardianId.equals(notification.getRecipientId())));
        assertThat(jpaInboxNotificationRepository.findAll())
            .noneMatch(notification -> notification.getRecipientType() == InboxRecipientType.CHILD);
    }

    private ChildAccountChangedEvent accountChanged(boolean deleted) {
        return new ChildAccountChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new ChildAccountChangedEvent.Data(accountId, childProfileId, 9L, deleted));
    }

    private List<InboxNotification> childInbox() {
        return jpaInboxNotificationRepository.findAll().stream()
            .filter(notification -> notification.getRecipientType() == InboxRecipientType.CHILD
                && accountId.equals(notification.getRecipientId()))
            .toList();
    }

    // 보내는 쪽 형식 그대로 넣는다
    private void send(String topic, DomainEvent event) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", event.eventId());
        body.put("eventType", event.eventType());
        body.put("schemaVersion", event.schemaVersion());
        body.put("occurredAt", event.occurredAt());
        body.put("producer", event.producer());
        body.put("aggregateId", event.aggregateId());
        body.put("correlationId", event.correlationId());
        body.put("data", event.data());
        var record = new ProducerRecord<>(topic, event.correlationId(), jsonMapper.writeValueAsString(body));
        record.headers().add("eventId", event.eventId().toString().getBytes(UTF_8));
        record.headers().add("eventType", event.eventType().getBytes(UTF_8));
        try (var producer = new KafkaProducer<String, String>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(record).get(10, TimeUnit.SECONDS);
        }
    }
}
