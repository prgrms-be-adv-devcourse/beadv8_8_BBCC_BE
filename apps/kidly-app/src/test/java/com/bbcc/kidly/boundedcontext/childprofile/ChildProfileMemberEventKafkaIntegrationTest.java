package com.bbcc.kidly.boundedcontext.childprofile;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileService;
import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileRequest;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfile;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
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
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
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
 * member.withdrawn이 커밋되면 kidly.member.events를 거쳐 그 회원의 자녀를 모두 삭제한다(#569).
 * 리스너는 이 테스트 안에서만 켜고 끈다. 같은 토픽에는 다른 테스트의 회원 이벤트도 쌓여 이어서 받을 수 있으므로
 * 회원 ID를 테스트마다 새로 만들고, 결과는 이 테스트의 회원 ID · 키로 걸러 본다.
 * 자녀마다 삭제 이벤트를 발행하는 것은 서비스 동작이라 ChildProfileServiceIntegrationTest가 본다.
 */
@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ChildProfileMemberEventKafkaIntegrationTest {

    private static final String TOPIC = "kidly.member.events";
    private static final String DLT = "kidly.member.events.childprofile.dlt";
    private static final String GROUP = "kidly.childprofile";
    private static final String LISTENER_ID = "childProfileMemberEventListener";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final ChildProfileRequest REQUEST = new ChildProfileRequest(
        "첫째", LocalDate.of(2019, 1, 15), Gender.MALE, new BigDecimal("115.0"), new BigDecimal("20.0"),
        BodyType.NORMAL, PreferredFit.LOOSE);

    // 재시도는 1초 간격 3번이라, 재시도했다면 DLT에 들어가기까지 3초 이상 걸린다
    private static final long RETRY_BACKOFF_TOTAL_MS = 3_000;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ChildProfileService childProfileService;

    @Autowired
    private ChildProfileRepository childProfileRepository;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private KafkaContainer kafka;

    private final Long memberId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    private final Long otherMemberId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);

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
    @DisplayName("탈퇴 이벤트가 커밋되면 Kafka로 받아 그 회원의 자녀를 모두 삭제한다")
    void deleteChildrenOnWithdraw() {
        childProfileService.register(memberId, REQUEST);
        childProfileService.register(memberId, REQUEST);
        var other = childProfileService.register(otherMemberId, REQUEST).id();

        publishInTransaction(memberWithdrawn());

        AsyncWait.until(TIMEOUT, () -> childrenOf(memberId).isEmpty());
        assertThat(childrenOf(otherMemberId)).extracting(ChildProfile::getId).containsExactly(other);
    }

    @Test
    @DisplayName("같은 탈퇴 이벤트를 두 번 받아도 결과는 한 번 받은 것과 같고 DLT로 보내지 않는다")
    void duplicateEvent() {
        childProfileService.register(memberId, REQUEST);
        var other = childProfileService.register(otherMemberId, REQUEST).id();
        var event = memberWithdrawn();

        publishInTransaction(event);
        publishInTransaction(event);

        // 두 번째에는 지울 자녀가 없어 결과가 바뀌지 않으므로, 그룹이 두 번째 메시지까지 처리 완료를 표시할 때까지 기다린다
        awaitProcessed(event.eventId());
        assertThat(childrenOf(memberId)).isEmpty();
        assertThat(childrenOf(otherMemberId)).extracting(ChildProfile::getId).containsExactly(other);
        assertThat(deadLetters()).isEmpty();
    }

    @Test
    @DisplayName("자녀 프로필이 받지 않는 종류는 건너뛰고 DLT로 보내지 않으며, 뒤 메시지를 처리한다")
    void skipOtherEventType() throws Exception {
        childProfileService.register(memberId, REQUEST);

        sendRaw("member.changed", "받지 않는 종류라 읽지 않는 본문");

        // 키(회원 ID)가 같아 같은 파티션이므로, 뒤 메시지를 처리했으면 앞 메시지는 이미 처리됐다
        publishInTransaction(memberWithdrawn());

        AsyncWait.until(TIMEOUT, () -> childrenOf(memberId).isEmpty());
        assertThat(deadLetters()).isEmpty();
    }

    @Test
    @DisplayName("받는 종류인데 본문을 읽지 못하면 재시도 없이 DLT로 보내고, 로그에 본문을 남기지 않는다")
    void deadLetterUnreadableBody(CapturedOutput output) throws Exception {
        var marker = UUID.randomUUID().toString();
        var body = "{\"correlationId\":\"" + marker + "\",\"data\":{\"memberId\":\"숫자 아님\"}}";

        var sent = sendRaw("member.withdrawn", body);

        assertDeadLetteredWithoutRetry(sent, body);
        assertThat(output.getOut()).contains("key=" + memberId).doesNotContain(marker);
    }

    private void assertDeadLetteredWithoutRetry(RecordMetadata sent, String body) {
        AsyncWait.until(TIMEOUT, () -> !deadLetters().isEmpty());
        assertThat(deadLetters()).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.key()).isEqualTo(String.valueOf(memberId));
            assertThat(deadLetter.value()).isEqualTo(body);
            assertThat(deadLetter.timestamp() - sent.timestamp()).isLessThan(RETRY_BACKOFF_TOTAL_MS);
        });
    }

    private List<ChildProfile> childrenOf(Long memberId) {
        return childProfileRepository.findAllByMemberId(memberId);
    }

    private void publishInTransaction(MemberWithdrawnEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private MemberWithdrawnEvent memberWithdrawn() {
        return new MemberWithdrawnEvent(UUID.randomUUID(), LocalDateTime.now(),
            new MemberWithdrawnEvent.Data(memberId));
    }

    // 회원은 보낸 순서대로 같은 파티션에 넣는다. 이 eventId 메시지 두 건이 토픽에 들어온 뒤, 그룹의 처리 완료 위치가 뒤의 것을 지나기를 기다린다
    private void awaitProcessed(UUID eventId) {
        AsyncWait.until(TIMEOUT, () -> recordsOf(eventId).size() == 2);
        var last = recordsOf(eventId).getLast();
        var partition = new TopicPartition(TOPIC, last.partition());
        AsyncWait.until(TIMEOUT, () -> committedOffset(partition) > last.offset());
    }

    private long committedOffset(TopicPartition partition) {
        try (var admin = Admin.create(Map.<String, Object>of(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            OffsetAndMetadata committed = admin.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata()
                .get(10, TimeUnit.SECONDS).get(partition);
            return committed == null ? -1 : committed.offset();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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

    // 토픽을 처음부터 지금 끝까지 읽어 이 eventId 헤더를 단 메시지만 offset 순으로 돌려준다
    private List<ConsumerRecord<String, String>> recordsOf(UUID eventId) {
        return readAll(TOPIC).stream()
            .filter(record -> eventId.toString().equals(header(record, "eventId")))
            .toList();
    }

    // DLT를 처음부터 지금 끝까지 읽어 이 테스트의 키만 돌려준다. 다른 테스트가 넣은 DLT 메시지도 있다
    private List<ConsumerRecord<String, String>> deadLetters() {
        return readAll(DLT).stream().filter(record -> String.valueOf(memberId).equals(record.key())).toList();
    }

    private List<ConsumerRecord<String, String>> readAll(String topic) {
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            var partitions = consumer.partitionsFor(topic).stream()
                .map(info -> new TopicPartition(topic, info.partition()))
                .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            var ends = consumer.endOffsets(partitions);
            var records = new ArrayList<ConsumerRecord<String, String>>();
            while (partitions.stream().anyMatch(partition -> consumer.position(partition) < ends.get(partition))) {
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
            }
            return records;
        }
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), UTF_8);
    }
}
