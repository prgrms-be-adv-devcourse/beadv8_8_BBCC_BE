package com.bbcc.kidly.boundedcontext.notification;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.notification.app.MemberContactService;
import com.bbcc.kidly.boundedcontext.notification.app.port.Email;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotification;
import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import com.bbcc.kidly.boundedcontext.notification.out.JpaInboxNotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.out.JpaNotificationRepository;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentRefundedEvent;
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
 * 결제가 kidly.payment.events에 보낸 지급 완료 · 환불 완료를 받아 메일을 보낸다(#879).
 * 이 서비스에는 결제 도메인이 없으므로 결제가 보내는 형식(키 correlationId, 헤더 eventType · eventId, 본문 공통 칸 + data)으로
 * 토픽에 직접 넣는다. 받는 사람 · 정산서 · 주문 ID를 테스트마다 새로 만들고, 결과는 이 테스트의 받는 사람으로 걸러 본다.
 */
@KafkaIntegrationTest
class NotificationPaymentEventKafkaIntegrationTest {

    private static final String TOPIC = "kidly.payment.events";
    private static final String LISTENER_ID = "notificationPaymentEventListener";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JpaNotificationRepository jpaNotificationRepository;

    @Autowired
    private JpaInboxNotificationRepository jpaInboxNotificationRepository;

    @Autowired
    private MemberContactService memberContactService;

    @Autowired
    private FakeNotificationEmailSender fakeNotificationEmailSender;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private KafkaContainer kafka;

    private final Long memberId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    private final Long targetId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    private final String email = "member-" + UUID.randomUUID() + "@kidly.co.kr";

    private MessageListenerContainer listener;

    @BeforeEach
    void startListener() {
        memberContactService.save(memberId, email);
        listener = kafkaListenerEndpointRegistry.getListenerContainer(LISTENER_ID);
        listener.start();
        AsyncWait.until(TIMEOUT, () -> !listener.getAssignedPartitions().isEmpty());
    }

    @AfterEach
    void stopListener() {
        listener.stop();
        fakeNotificationEmailSender.reset();
    }

    @Test
    @DisplayName("이 토픽은 책갈피가 없으므로 처음부터 읽어, 구독 전에 발행된 지급 · 환불을 놓치지 않는다")
    void readFromEarliest() {
        assertThat(listener.getContainerProperties().getKafkaConsumerProperties()
            .getProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)).isEqualTo("earliest");
    }

    @Test
    @DisplayName("지급 완료를 받으면 판매자에게 지급 금액 메일을 보낸다")
    void sendPayoutEmail() throws Exception {
        send(payoutCompleted(), targetId);

        AsyncWait.until(TIMEOUT, () -> statuses().equals(List.of(NotificationStatus.SENT)));
        assertThat(notifications()).singleElement()
            .satisfies(notification -> assertThat(notification.getType()).isEqualTo(NotificationType.PAYOUT_COMPLETED));
        assertThat(sent()).singleElement().satisfies(mail -> {
            assertThat(mail.subject()).isEqualTo("[Kidly] 정산금이 입금됐어요");
            assertThat(mail.text()).contains("12,000원");
        });
    }

    @Test
    @DisplayName("같은 정산서의 지급 완료가 새 이벤트 ID로 다시 와도 메일 · 알림함은 한 건이다")
    void duplicatePayout() throws Exception {
        send(payoutCompleted(), targetId);
        send(payoutCompleted(), targetId);

        // 키(정산서 ID)가 같아 같은 파티션이다. 뒤 메시지를 처리한 뒤 확인하도록 환불을 같은 키로 하나 더 보낸다
        send(refunded(), targetId);
        AsyncWait.until(TIMEOUT, () -> statuses().size() == 2
            && statuses().stream().allMatch(NotificationStatus.SENT::equals));
        assertThat(notifications()).filteredOn(n -> n.getType() == NotificationType.PAYOUT_COMPLETED).hasSize(1);
        assertThat(jpaInboxNotificationRepository.findAll()).filteredOn(n -> memberId.equals(n.getMemberId()))
            .extracting(InboxNotification::getType, InboxNotification::getLinkPath)
            .containsExactlyInAnyOrder(
                tuple(NotificationType.PAYOUT_COMPLETED, "/center/settlements/" + targetId),
                tuple(NotificationType.REFUND_COMPLETED, "/orders/" + targetId));
    }

    @Test
    @DisplayName("환불 완료를 받으면 환불받은 회원에게 환불 금액 메일을 보낸다")
    void sendRefundEmail() throws Exception {
        send(refunded(), targetId);

        AsyncWait.until(TIMEOUT, () -> statuses().equals(List.of(NotificationStatus.SENT)));
        assertThat(sent()).singleElement().satisfies(mail -> {
            assertThat(mail.subject()).isEqualTo("[Kidly] 환불이 완료됐어요");
            assertThat(mail.text()).contains("34,500원");
        });
    }

    private PaymentPayoutCompletedEvent payoutCompleted() {
        return new PaymentPayoutCompletedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new PaymentPayoutCompletedEvent.Data(targetId, memberId, 12_000L, 1L, LocalDateTime.now()));
    }

    private PaymentRefundedEvent refunded() {
        return new PaymentRefundedEvent(UUID.randomUUID(), LocalDateTime.now(), new PaymentRefundedEvent.Data(
            1L, 2L, targetId, null, List.of(new PaymentRefundedEvent.Item(3L, 34_500L)), 34_500L,
            LocalDateTime.now(), memberId));
    }

    private List<Notification> notifications() {
        return jpaNotificationRepository.findAll().stream()
            .filter(notification -> email.equals(notification.getRecipient()))
            .toList();
    }

    private List<NotificationStatus> statuses() {
        return notifications().stream().map(Notification::getStatus).toList();
    }

    private List<Email> sent() {
        return fakeNotificationEmailSender.sent().stream().filter(mail -> mail.to().equals(email)).toList();
    }

    // 결제가 보내는 형식 그대로 넣는다
    private void send(DomainEvent event, Long key) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", event.eventId());
        body.put("eventType", event.eventType());
        body.put("schemaVersion", event.schemaVersion());
        body.put("occurredAt", event.occurredAt());
        body.put("producer", event.producer());
        body.put("aggregateId", event.aggregateId());
        body.put("correlationId", event.correlationId());
        body.put("data", event.data());
        var record = new ProducerRecord<>(TOPIC, String.valueOf(key), jsonMapper.writeValueAsString(body));
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
