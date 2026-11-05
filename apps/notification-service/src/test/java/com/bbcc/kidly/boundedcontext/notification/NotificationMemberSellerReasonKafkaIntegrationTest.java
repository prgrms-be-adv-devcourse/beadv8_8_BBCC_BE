package com.bbcc.kidly.boundedcontext.notification;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.app.MemberContactService;
import com.bbcc.kidly.boundedcontext.notification.app.port.Email;
import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus;
import com.bbcc.kidly.boundedcontext.notification.out.JpaNotificationRepository;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.member.event.MemberSuspendedEvent;
import com.bbcc.kidly.shared.seller.event.SellerApplicationRejectedEvent;
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
/**
 * 회원이 보낸 계정 정지(kidly.member.events) · 판매자 신청 거절(kidly.seller.events)을 받아 사유 메일을 보낸다(#880).
 * 회원이 보내는 형식(키 correlationId, 헤더 eventType · eventId, 본문 공통 칸 + data)으로 토픽에 직접 넣는다.
 * 받는 사람을 테스트마다 새로 만들고, 결과는 이 테스트의 받는 사람으로 걸러 본다.
 */
@KafkaIntegrationTest
class NotificationMemberSellerReasonKafkaIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JpaNotificationRepository jpaNotificationRepository;

    @Autowired
    private MemberContactService memberContactService;

    @Autowired
    private FakeNotificationEmailSender fakeNotificationEmailSender;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private KafkaContainer kafka;

    private final Long memberId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    private final String email = "member-" + UUID.randomUUID() + "@kidly.co.kr";

    private List<MessageListenerContainer> listeners;

    @BeforeEach
    void startListeners() {
        memberContactService.save(memberId, email);
        listeners = List.of(kafkaListenerEndpointRegistry.getListenerContainer("notificationMemberEventListener"),
            kafkaListenerEndpointRegistry.getListenerContainer("notificationSellerEventListener"));
        listeners.forEach(MessageListenerContainer::start);
        AsyncWait.until(TIMEOUT, () -> listeners.stream().noneMatch(l -> l.getAssignedPartitions().isEmpty()));
    }

    @AfterEach
    void stopListeners() {
        listeners.forEach(MessageListenerContainer::stop);
        fakeNotificationEmailSender.reset();
    }

    @Test
    @DisplayName("판매자 토픽은 책갈피가 없으므로 처음부터 읽어, 구독 전에 발행된 거절을 놓치지 않는다")
    void sellerReadFromEarliest() {
        assertThat(listeners.get(1).getContainerProperties().getKafkaConsumerProperties()
            .getProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)).isEqualTo("earliest");
    }

    @Test
    @DisplayName("계정 정지를 받으면 정지 사유 메일을 보낸다")
    void sendSuspendedEmail() throws Exception {
        send("kidly.member.events", new MemberSuspendedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new MemberSuspendedEvent.Data(memberId, "운영 정책 위반", LocalDateTime.now())));

        AsyncWait.until(TIMEOUT, () -> statuses().equals(List.of(NotificationStatus.SENT)));
        assertThat(notifications()).singleElement()
            .satisfies(n -> assertThat(n.getType()).isEqualTo(NotificationType.MEMBER_SUSPENDED));
        assertThat(sent()).singleElement()
            .satisfies(mail -> assertThat(mail.text()).contains("정지 사유: 운영 정책 위반"));
    }

    @Test
    @DisplayName("판매자 신청 거절을 받으면 신청자에게 거절 사유 메일을 보낸다")
    void sendApplicationRejectedEmail() throws Exception {
        send("kidly.seller.events", new SellerApplicationRejectedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new SellerApplicationRejectedEvent.Data(1L, memberId, "사업자 정보 불일치", LocalDateTime.now())));

        AsyncWait.until(TIMEOUT, () -> statuses().equals(List.of(NotificationStatus.SENT)));
        assertThat(notifications()).singleElement()
            .satisfies(n -> assertThat(n.getType()).isEqualTo(NotificationType.SELLER_APPLICATION_REJECTED));
        assertThat(sent()).singleElement()
            .satisfies(mail -> assertThat(mail.text()).contains("거절 사유: 사업자 정보 불일치"));
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

    // 회원이 보내는 형식 그대로 넣는다
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
