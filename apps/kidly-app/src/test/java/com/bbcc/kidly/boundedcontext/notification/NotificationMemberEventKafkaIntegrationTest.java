package com.bbcc.kidly.boundedcontext.notification;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.app.port.Email;
import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationChannel;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import com.bbcc.kidly.boundedcontext.notification.out.JpaNotificationRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
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
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;

/**
 * member.registered가 커밋되면 kidly.member.events를 거쳐 환영 메일을 저장하고 별도 스레드에서 보낸다(#567).
 * 리스너는 이 테스트 안에서만 켜고 끈다. 같은 토픽에는 다른 테스트의 회원 이벤트도 쌓여 이어서 받을 수 있으므로
 * 회원 ID · 이메일을 테스트마다 새로 만들고, 결과는 이 테스트의 받는 사람 · 키로 걸러 본다.
 */
@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class NotificationMemberEventKafkaIntegrationTest {

    private static final String TOPIC = "kidly.member.events";
    private static final String DLT = "kidly.member.events.notification.dlt";
    private static final String LISTENER_ID = "notificationMemberEventListener";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    // 재시도는 1초 간격 3번이라, 재시도했다면 DLT에 들어가기까지 3초 이상 걸린다
    private static final long RETRY_BACKOFF_TOTAL_MS = 3_000;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JpaNotificationRepository jpaNotificationRepository;

    @Autowired
    private FakeNotificationEmailSender fakeNotificationEmailSender;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private KafkaContainer kafka;

    private final Long memberId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    private final String email = "parent-" + UUID.randomUUID() + "@kidly.co.kr";

    private MessageListenerContainer listener;

    // 읽기 시작 위치가 latest라 파티션을 배정받기 전에 보낸 메시지는 받지 못한다
    @BeforeEach
    void startListener() {
        listener = kafkaListenerEndpointRegistry.getListenerContainer(LISTENER_ID);
        listener.start();
        AsyncWait.until(TIMEOUT, () -> !listener.getAssignedPartitions().isEmpty());
    }

    // stop()은 처리 중인 메시지가 끝날 때까지 기다리므로 뒤이은 테이블 비우기와 겹치지 않는다
    @AfterEach
    void stopListener() {
        listener.stop();
        fakeNotificationEmailSender.reset();
    }

    @Test
    @DisplayName("가입 이벤트가 커밋되면 Kafka로 받아 환영 메일을 저장하고 비동기로 보낸다")
    void sendWelcomeEmail() {
        publishInTransaction(memberRegistered(email));

        AsyncWait.until(TIMEOUT, () -> statusesOf(email).equals(List.of(NotificationStatus.SENT)));
        assertThat(notificationsOf(email)).singleElement().satisfies(notification -> {
            assertThat(notification.getChannel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(notification.getType()).isEqualTo(NotificationType.WELCOME);
            assertThat(notification.getMemberId()).isEqualTo(memberId);
            assertThat(notification.getRecipient()).isEqualTo(email);
            assertThat(notification.getAttemptCount()).isEqualTo(1);
        });
        assertThat(sentTo(email)).singleElement().satisfies(sent -> {
            assertThat(sent.subject()).isEqualTo("[Kidly] 가입을 환영합니다");
            assertThat(sent.text()).contains("http://localhost:5173");
        });
    }

    @Test
    @DisplayName("같은 가입 이벤트를 두 번 받아도 메일은 한 통이다")
    void duplicateEvent() {
        var event = memberRegistered(email);

        publishInTransaction(event);
        publishInTransaction(event);

        AsyncWait.until(TIMEOUT, () -> statusesOf(email).equals(List.of(NotificationStatus.SENT)));
        assertThat(sentTo(email)).hasSize(1);
    }

    @Test
    @DisplayName("초기 데이터처럼 예약 도메인으로 가입하면 메일을 보내지 않는다")
    void skipReservedDomain() {
        var reserved = "parent-" + UUID.randomUUID() + "@kidly.test";

        publishInTransaction(memberRegistered(reserved));

        AsyncWait.until(TIMEOUT, () -> statusesOf(reserved).equals(List.of(NotificationStatus.SKIPPED)));
        assertThat(sentTo(reserved)).isEmpty();
    }

    @Test
    @DisplayName("알림이 받지 않는 종류는 건너뛰고 DLT로 보내지 않으며, 뒤 메시지를 처리한다")
    void skipOtherEventType() throws Exception {
        sendRaw("member.changed", "받지 않는 종류라 읽지 않는 본문");

        // 키(회원 ID)가 같아 같은 파티션이므로, 뒤 메시지를 처리했으면 앞 메시지는 이미 처리됐다
        publishInTransaction(memberRegistered(email));

        AsyncWait.until(TIMEOUT, () -> statusesOf(email).equals(List.of(NotificationStatus.SENT)));
        assertThat(deadLetters()).isEmpty();
    }

    @Test
    @DisplayName("받는 종류인데 본문을 읽지 못하면 재시도 없이 DLT로 보내고, 로그에 본문을 남기지 않는다")
    void deadLetterUnreadableBody(CapturedOutput output) throws Exception {
        var body = "{\"data\":{\"memberId\":\"숫자 아님\",\"email\":\"" + email + "\"}}";

        var sent = sendRaw("member.registered", body);

        assertDeadLetteredWithoutRetry(sent, body);
        assertThat(output.getOut()).contains("key=" + memberId).doesNotContain(email);
    }

    private void assertDeadLetteredWithoutRetry(RecordMetadata sent, String body) {
        AsyncWait.until(TIMEOUT, () -> !deadLetters().isEmpty());
        assertThat(deadLetters()).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.key()).isEqualTo(String.valueOf(memberId));
            assertThat(deadLetter.value()).isEqualTo(body);
            assertThat(deadLetter.timestamp() - sent.timestamp()).isLessThan(RETRY_BACKOFF_TOTAL_MS);
        });
    }

    private List<Notification> notificationsOf(String recipient) {
        return jpaNotificationRepository.findAll().stream()
            .filter(notification -> recipient.equals(notification.getRecipient()))
            .toList();
    }

    private List<NotificationStatus> statusesOf(String recipient) {
        return notificationsOf(recipient).stream().map(Notification::getStatus).toList();
    }

    private List<Email> sentTo(String recipient) {
        return fakeNotificationEmailSender.sent().stream().filter(sent -> sent.to().equals(recipient)).toList();
    }

    private void publishInTransaction(MemberRegisteredEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private MemberRegisteredEvent memberRegistered(String recipient) {
        return new MemberRegisteredEvent(UUID.randomUUID(), LocalDateTime.now(),
            new MemberRegisteredEvent.Data(memberId, recipient));
    }

    // 회원이 보내지 않는 메시지(다른 종류 · 깨진 본문)를 이 테스트의 회원 ID 키로 직접 넣는다
    private RecordMetadata sendRaw(String eventType, String body) throws Exception {
        var record = new ProducerRecord<>(TOPIC, String.valueOf(memberId), body);
        record.headers().add("eventId", UUID.randomUUID().toString().getBytes(UTF_8));
        record.headers().add("eventType", eventType.getBytes(UTF_8));
        try (var producer = new KafkaProducer<String, String>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            return producer.send(record).get(10, TimeUnit.SECONDS);
        }
    }

    // DLT를 처음부터 지금 끝까지 읽어 이 테스트의 키만 돌려준다. 다른 테스트가 넣은 DLT 메시지도 있다
    private List<ConsumerRecord<String, String>> deadLetters() {
        var partition = new TopicPartition(DLT, 0);
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));
            var end = consumer.endOffsets(List.of(partition)).get(partition);
            var records = new ArrayList<ConsumerRecord<String, String>>();
            while (consumer.position(partition) < end) {
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
            }
            return records.stream().filter(record -> String.valueOf(memberId).equals(record.key())).toList();
        }
    }
}
