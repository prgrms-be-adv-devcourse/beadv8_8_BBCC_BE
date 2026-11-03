package com.bbcc.kidly.boundedcontext.seller;

import static com.bbcc.kidly.boundedcontext.seller.SellerFixture.NOW;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.member.event.MemberChangedEvent;
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
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * member.changed가 커밋되면 kidly.member.events를 거쳐 그 회원의 판매자 신청 닉네임을 바꾼다(#568).
 * 리스너는 이 테스트 안에서만 켜고 끈다. 같은 토픽에는 다른 테스트의 회원 이벤트도 쌓여 이어서 받을 수 있으므로
 * 회원 ID를 테스트마다 새로 만들고, 결과는 이 테스트의 신청 · 키로 걸러 본다.
 */
@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class SellerMemberEventKafkaIntegrationTest {

    private static final String TOPIC = "kidly.member.events";
    private static final String GROUP = "kidly.seller";
    private static final String DLT = "kidly.member.events.seller.dlt";
    private static final String LISTENER_ID = "sellerMemberEventListener";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    // 재시도는 1초 간격 3번이라, 재시도했다면 DLT에 들어가기까지 3초 이상 걸린다
    private static final long RETRY_BACKOFF_TOTAL_MS = 3_000;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private SellerApplicationRepository sellerApplicationRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private KafkaContainer kafka;

    private final Long memberId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);

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
    }

    @Test
    @DisplayName("닉네임 변경 이벤트가 커밋되면 Kafka로 받아 그 회원의 신청 닉네임을 모두 바꾼다")
    void changeApplicantNickname() {
        // 회원당 심사 중인 신청은 1건만 있을 수 있어서 거절 1건 + 심사 중 1건
        var rejected = sellerApplicationRepository.save(SellerFixture.rejectedApplication(memberId)).getId();
        var pending = sellerApplicationRepository.save(SellerFixture.application(memberId)).getId();
        var otherMemberId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        var other = sellerApplicationRepository.save(SellerFixture.application(otherMemberId)).getId();

        publishInTransaction(memberChanged("하은맘", NOW));

        AsyncWait.until(TIMEOUT, () -> "하은맘".equals(nicknameOf(pending)));
        assertThat(nicknameOf(rejected)).isEqualTo("하은맘");
        assertThat(nicknameOf(other)).isEqualTo(SellerFixture.APPLICANT_NICKNAME);
    }

    @Test
    @DisplayName("이미 반영한 변경보다 이른 변경은 반영하지 않는다")
    void ignoreEarlierChange() throws Exception {
        var application = sellerApplicationRepository.save(SellerFixture.application(memberId)).getId();
        publishInTransaction(memberChanged("하은맘", NOW));
        AsyncWait.until(TIMEOUT, () -> "하은맘".equals(nicknameOf(application)));

        var sent = sendChanged(memberChanged("예전닉네임", NOW.minusDays(1)));

        awaitProcessed(sent);
        assertThat(nicknameOf(application)).isEqualTo("하은맘");
    }

    @Test
    @DisplayName("같은 변경 이벤트를 두 번 받아도 결과는 한 번 받은 것과 같다")
    void duplicateEvent() throws Exception {
        var application = sellerApplicationRepository.save(SellerFixture.application(memberId)).getId();
        var event = memberChanged("하은맘", NOW);

        sendChanged(event);
        var second = sendChanged(event);

        awaitProcessed(second);
        var changed = sellerApplicationRepository.getById(application);
        assertThat(changed.getApplicantNickname()).isEqualTo("하은맘");
        assertThat(changed.getApplicantNicknameChangedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("판매자가 받지 않는 종류는 건너뛰고 DLT로 보내지 않으며, 뒤 메시지를 처리한다")
    void skipOtherEventType() throws Exception {
        var application = sellerApplicationRepository.save(SellerFixture.application(memberId)).getId();
        sendRaw("member.registered", UUID.randomUUID(), "받지 않는 종류라 읽지 않는 본문");

        // 키(회원 ID)가 같아 같은 파티션이므로, 뒤 메시지를 처리했으면 앞 메시지는 이미 처리됐다
        publishInTransaction(memberChanged("하은맘", NOW));

        AsyncWait.until(TIMEOUT, () -> "하은맘".equals(nicknameOf(application)));
        assertThat(deadLetters()).isEmpty();
    }

    @Test
    @DisplayName("받는 종류인데 본문을 읽지 못하면 재시도 없이 DLT로 보내고, 로그에 닉네임을 남기지 않는다")
    void deadLetterUnreadableBody(CapturedOutput output) throws Exception {
        var nickname = "닉네임-" + UUID.randomUUID();
        var body = "{\"data\":{\"memberId\":\"숫자 아님\",\"nickname\":\"" + nickname + "\"}}";

        var sent = sendRaw("member.changed", UUID.randomUUID(), body);

        assertDeadLetteredWithoutRetry(sent, body);
        assertThat(output.getOut()).contains("key=" + memberId).doesNotContain(nickname);
    }

    private void assertDeadLetteredWithoutRetry(RecordMetadata sent, String body) {
        AsyncWait.until(TIMEOUT, () -> !deadLetters().isEmpty());
        assertThat(deadLetters()).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.key()).isEqualTo(String.valueOf(memberId));
            assertThat(deadLetter.value()).isEqualTo(body);
            assertThat(deadLetter.timestamp() - sent.timestamp()).isLessThan(RETRY_BACKOFF_TOTAL_MS);
        });
    }

    private String nicknameOf(Long applicationId) {
        return sellerApplicationRepository.getById(applicationId).getApplicantNickname();
    }

    private void publishInTransaction(MemberChangedEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private MemberChangedEvent memberChanged(String nickname, LocalDateTime occurredAt) {
        return new MemberChangedEvent(UUID.randomUUID(), occurredAt, new MemberChangedEvent.Data(memberId, nickname));
    }

    // 보낸 결과를 위치로 기다려야 하는 변경은 직접 넣는다. 리스너는 본문에서 eventId · occurredAt · data만 읽는다
    private RecordMetadata sendChanged(MemberChangedEvent event) throws Exception {
        return sendRaw("member.changed", event.eventId(), jsonMapper.writeValueAsString(event));
    }

    // 회원이 보내지 않는 메시지(다른 종류 · 깨진 본문)를 이 테스트의 회원 ID 키로 직접 넣는다
    private RecordMetadata sendRaw(String eventType, UUID eventId, String body) throws Exception {
        var record = new ProducerRecord<>(TOPIC, String.valueOf(memberId), body);
        record.headers().add("eventId", eventId.toString().getBytes(UTF_8));
        record.headers().add("eventType", eventType.getBytes(UTF_8));
        try (var producer = new KafkaProducer<String, String>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            return producer.send(record).get(10, TimeUnit.SECONDS);
        }
    }

    // 결과가 바뀌지 않는 메시지는 처리했는지 결과로 알 수 없다. 한 건씩 처리 완료를 커밋하므로, 판매자 그룹의 커밋 위치가 그 메시지를 지나면 처리한 것이다
    private void awaitProcessed(RecordMetadata sent) {
        var partition = new TopicPartition(TOPIC, sent.partition());
        var config = Map.<String, Object>of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        try (var admin = Admin.create(config)) {
            AsyncWait.until(TIMEOUT, () -> committedOffset(admin, partition) > sent.offset());
        }
    }

    private static long committedOffset(Admin admin, TopicPartition partition) {
        try {
            var committed = admin.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata()
                .get(10, TimeUnit.SECONDS).get(partition);
            return committed == null ? -1 : committed.offset();
        } catch (Exception e) {
            throw new IllegalStateException("판매자 그룹의 커밋 위치를 읽지 못했습니다", e);
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
