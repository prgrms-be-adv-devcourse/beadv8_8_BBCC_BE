package com.bbcc.kidly.gateway.revocation;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.gateway.support.AsyncWait;
import com.bbcc.kidly.gateway.support.KafkaIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.RecordsToDelete;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicExistsException;
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
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회원이 Kafka로 보낸 member.tokens-revoked를 Gateway가 받아 무효화 목록에 넣는지 실제 Kafka로 본다(#608).
 * 메시지는 회원이 보내는 모양(키 = 회원 ID, 헤더 eventType · eventId, 본문 = 공통 칸 7개 + data)으로 직접 넣는다.
 * Gateway는 회원 토픽을 선언하지 않으므로(보내는 쪽이 선언) 테스트가 만든다.
 * 리스너는 켤 때마다 31분 전으로 되감으므로 앞 테스트의 메시지를 다시 읽는다. 테스트마다 회원 ID를 새로 뽑아 섞이지 않게 한다.
 */
@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class MemberTokensRevokedKafkaIntegrationTest {

    private static final String TOPIC = "kidly.member.events";
    private static final String DLT = "kidly.member.events.gateway.dlt";
    private static final String LISTENER_ID = "gatewayMemberEventListener";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // 무효화 시각(지금)보다 먼저 발급된 토큰. 무효화가 반영됐으면 거절된다
    private static final Duration ISSUED_BEFORE = Duration.ofMinutes(1);

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private AccessTokenRevocations revocations;

    @BeforeEach
    void createTopic() {
        try (Admin admin = admin()) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 3, (short) 1))).all().get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof TopicExistsException)) {
                throw new IllegalStateException("토픽을 만들지 못했습니다: " + TOPIC, e);
            }
        } catch (Exception e) {
            throw new IllegalStateException("토픽을 만들지 못했습니다: " + TOPIC, e);
        }
    }

    // stop()은 처리 중인 메시지가 끝날 때까지 기다린다
    @AfterEach
    void stopListener() {
        registry.getListenerContainer(LISTENER_ID).stop();
    }

    @Test
    @DisplayName("다시 뜨면 이미 읽은 무효화도 되감아 다시 읽어 목록에 넣는다")
    void rewindsOnAssignment() throws Exception {
        var memberId = newMemberId();
        send(null, null, memberId, "member.tokens-revoked", tokensRevoked(memberId));
        // 앞 인스턴스가 이 메시지까지 읽고 책갈피를 남긴 뒤 다시 뜬 상황. 책갈피대로 읽으면 이 메시지를 받지 못한다
        commitGroupOffsetsToEnd();

        startListener();

        AsyncWait.until(TIMEOUT, () -> isRevoked(memberId));
    }

    @Test
    @DisplayName("무효화 시각을 한국 시간으로 읽는다: 그 시각까지 발급된 토큰은 거절하고 1초 뒤 발급된 토큰은 통과한다")
    void readsRevokedAtAsKst() throws Exception {
        var memberId = newMemberId();
        var revokedAt = LocalDateTime.now(KST).truncatedTo(ChronoUnit.SECONDS);
        startListener();

        send(null, null, memberId, "member.tokens-revoked", envelope("member.tokens-revoked", memberId,
            Map.of("memberId", memberId, "revokedAt", revokedAt.toString())));

        AsyncWait.until(TIMEOUT, () -> isRevoked(memberId));
        // 다른 시간대로 읽으면 무효화 시각이 몇 시간 밀려 1초 뒤 토큰도 거절되거나 그 시각 토큰이 통과한다
        var revokedInstant = revokedAt.atZone(KST).toInstant();
        var now = Instant.now();
        assertThat(revocations.isRevoked(memberId, revokedInstant, now)).isTrue();
        assertThat(revocations.isRevoked(memberId, revokedInstant.plusSeconds(1), now)).isFalse();
    }

    @Test
    @DisplayName("31분보다 오래된 메시지는 되감아도 읽지 않는다")
    void skipsOlderThanRetention() throws Exception {
        var oldMemberId = newMemberId();
        var memberId = newMemberId();
        // 되감기는 그 시각 이후 첫 메시지로 간다. 오래된 메시지가 최근 메시지보다 앞에 오도록 파티션을 비우고 둘을 같은 파티션에 넣는다
        // (앞 테스트의 최근 메시지가 앞에 있으면 그 위치로 되감겨 오래된 메시지도 읽힌다)
        clearPartition(0);
        var fortyMinutesAgo = Instant.now().minus(Duration.ofMinutes(40)).toEpochMilli();
        // 본문의 무효화 시각은 지금이라 읽히면 거절된다. 메시지 시각(timestamp)만 40분 전이다
        send(0, fortyMinutesAgo, oldMemberId, "member.tokens-revoked", tokensRevoked(oldMemberId));
        send(0, null, memberId, "member.tokens-revoked", tokensRevoked(memberId));

        startListener();

        // 같은 파티션의 뒤 메시지가 반영됐으면 앞 메시지는 읽혔거나 건너뛰었다
        AsyncWait.until(TIMEOUT, () -> isRevoked(memberId));
        assertThat(isRevoked(oldMemberId)).isFalse();
    }

    @Test
    @DisplayName("Gateway가 받지 않는 종류는 건너뛰고 DLT로 보내지 않는다")
    void skipsOtherEventType() throws Exception {
        var otherMemberId = newMemberId();
        var memberId = newMemberId();
        startListener();

        // data가 무효화와 같은 모양이라 종류를 거르지 않으면 목록에 들어간다
        send(1, null, otherMemberId, "member.changed", envelope("member.changed", otherMemberId, data(otherMemberId)));
        send(1, null, memberId, "member.tokens-revoked", tokensRevoked(memberId));

        // 같은 파티션의 뒤 메시지가 반영됐으면 앞 메시지는 이미 처리됐다
        AsyncWait.until(TIMEOUT, () -> isRevoked(memberId));
        assertThat(isRevoked(otherMemberId)).isFalse();
        assertThat(deadLetters(otherMemberId)).isEmpty();
    }

    @Test
    @DisplayName("본문을 읽지 못하면 DLT로 보내고, 로그에 본문을 남기지 않는다")
    void deadLettersUnreadableBody(CapturedOutput output) throws Exception {
        var memberId = newMemberId();
        var secret = "본문-" + UUID.randomUUID();
        var body = "{\"data\":{\"memberId\":\"" + secret;
        startListener();

        send(null, null, memberId, "member.tokens-revoked", body);

        AsyncWait.until(TIMEOUT, () -> !deadLetters(memberId).isEmpty());
        assertThat(deadLetters(memberId)).singleElement()
            .satisfies(deadLetter -> assertThat(deadLetter.value()).isEqualTo(body));
        assertThat(output.getOut()).contains("key=" + memberId).doesNotContain(secret);
    }

    private void startListener() {
        registry.getListenerContainer(LISTENER_ID).start();
    }

    private boolean isRevoked(long memberId) {
        var now = Instant.now();
        return revocations.isRevoked(memberId, now.minus(ISSUED_BEFORE), now);
    }

    private static long newMemberId() {
        return ThreadLocalRandom.current().nextLong(1, 1_000_000_000L);
    }

    private String tokensRevoked(long memberId) {
        return envelope("member.tokens-revoked", memberId, data(memberId));
    }

    private static Map<String, Object> data(long memberId) {
        return Map.of("memberId", memberId, "revokedAt", nowInKst());
    }

    // 회원이 보내는 본문과 같은 모양: 공통 칸 7개 + data. 시각은 시간대 없는 한국 시간
    private String envelope(String eventType, long memberId, Map<String, Object> data) {
        var body = new LinkedHashMap<String, Object>();
        body.put("eventId", UUID.randomUUID().toString());
        body.put("eventType", eventType);
        body.put("schemaVersion", 1);
        body.put("occurredAt", nowInKst());
        body.put("producer", "member");
        body.put("aggregateId", String.valueOf(memberId));
        body.put("correlationId", String.valueOf(memberId));
        body.put("data", data);
        return jsonMapper.writeValueAsString(body);
    }

    private static String nowInKst() {
        return LocalDateTime.now(KST).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    // partition · timestamp가 null이면 키로 파티션을 고르고 보내는 시각을 쓴다
    private void send(Integer partition, Long timestamp, long memberId, String eventType, String body)
        throws Exception {
        var record = new ProducerRecord<>(TOPIC, partition, timestamp, String.valueOf(memberId), body);
        record.headers().add("eventId", UUID.randomUUID().toString().getBytes(UTF_8));
        record.headers().add("eventType", eventType.getBytes(UTF_8));
        try (var producer = new KafkaProducer<String, String>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(record).get(10, TimeUnit.SECONDS);
        }
    }

    // 리스너가 꺼져 있을 때(그룹에 소비자가 없을 때)만 바꿀 수 있다
    private void commitGroupOffsetsToEnd() throws Exception {
        var partitions = List.of(
            new TopicPartition(TOPIC, 0), new TopicPartition(TOPIC, 1), new TopicPartition(TOPIC, 2));
        try (Admin admin = admin(); var consumer = consumer()) {
            var offsets = new HashMap<TopicPartition, OffsetAndMetadata>();
            consumer.endOffsets(partitions)
                .forEach((partition, end) -> offsets.put(partition, new OffsetAndMetadata(end)));
            admin.alterConsumerGroupOffsets(GatewayKafkaListenerConfig.GROUP, offsets).all().get(10, TimeUnit.SECONDS);
        }
    }

    // 지금 끝까지의 메시지를 지운다. 이 토픽은 이 테스트만 쓴다
    private void clearPartition(int partition) throws Exception {
        var topicPartition = new TopicPartition(TOPIC, partition);
        try (Admin admin = admin(); var consumer = consumer()) {
            long end = consumer.endOffsets(List.of(topicPartition)).get(topicPartition);
            admin.deleteRecords(Map.of(topicPartition, RecordsToDelete.beforeOffset(end))).all()
                .get(10, TimeUnit.SECONDS);
        }
    }

    // DLT를 처음부터 지금 끝까지 읽어 이 회원 키만 돌려준다. 다른 테스트가 넣은 DLT 메시지도 있다
    private List<ConsumerRecord<String, String>> deadLetters(long memberId) {
        var partition = new TopicPartition(DLT, 0);
        try (var consumer = consumer()) {
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));
            long end = consumer.endOffsets(List.of(partition)).get(partition);
            var records = new ArrayList<ConsumerRecord<String, String>>();
            while (consumer.position(partition) < end) {
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
            }
            return records.stream().filter(record -> String.valueOf(memberId).equals(record.key())).toList();
        }
    }

    private Admin admin() {
        return Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()));
    }

    private KafkaConsumer<String, String> consumer() {
        return new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }
}
