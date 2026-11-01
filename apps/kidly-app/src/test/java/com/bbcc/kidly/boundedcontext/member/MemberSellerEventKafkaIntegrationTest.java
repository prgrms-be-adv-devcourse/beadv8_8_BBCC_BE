package com.bbcc.kidly.boundedcontext.member;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.app.AuthTokenService;
import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.SellerState;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.auth.Role;
import com.bbcc.kidly.shared.member.event.MemberChangedEvent;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.IssuedAccessToken;
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
import org.apache.kafka.common.header.Header;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * seller.changed가 커밋되면 kidly.seller.events를 거쳐 회원의 판매자 상태 복사본을 갱신하고,
 * SELLER를 잃으면 Access Token을 무효화한다(#608). 무효화는 kidly.member.events에 온 member.tokens-revoked로 본다.
 * 리스너는 이 테스트 안에서만 켜고 끈다. 같은 토픽에는 다른 테스트의 판매자 이벤트(판매자 ID 1, 2 …)도 쌓여 이어서 받을 수 있으므로
 * 회원 ID를 테스트마다 무작위로 정해 저장하고, 결과는 이 테스트의 회원 · 키로 걸러 본다.
 */
@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class MemberSellerEventKafkaIntegrationTest {

    private static final String TOPIC = "kidly.seller.events";
    private static final String MEMBER_TOPIC = "kidly.member.events";
    private static final String GROUP = "kidly.member";
    private static final String DLT = "kidly.seller.events.member.dlt";
    private static final String LISTENER_ID = "memberSellerEventListener";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final LocalDateTime APPROVED_AT = LocalDateTime.of(2026, 10, 13, 9, 0);

    // 재시도는 1초 간격 3번이라, 재시도했다면 DLT에 들어가기까지 3초 이상 걸린다
    private static final long RETRY_BACKOFF_TOTAL_MS = 3_000;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private KafkaContainer kafka;

    private final Long memberId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);

    private MessageListenerContainer listener;

    private KafkaConsumer<String, String> memberEventConsumer;
    private final List<ConsumerRecord<String, String>> memberEvents = new ArrayList<>();

    // 읽기 시작 위치가 latest라 파티션을 배정받기 전에 보낸 메시지는 받지 못한다
    @BeforeEach
    void startListener() {
        listener = kafkaListenerEndpointRegistry.getListenerContainer(LISTENER_ID);
        listener.start();
        AsyncWait.until(TIMEOUT, () -> !listener.getAssignedPartitions().isEmpty());
        memberEventConsumer = new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "member-seller-test-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        memberEventConsumer.subscribe(List.of(MEMBER_TOPIC));
    }

    // stop()은 처리 중인 메시지가 끝날 때까지 기다리므로 뒤이은 테이블 비우기와 겹치지 않는다
    @AfterEach
    void stopListener() {
        listener.stop();
        memberEventConsumer.close();
    }

    @Test
    @DisplayName("판매자가 승인되면 Kafka로 받아 회원에 상태 복사본을 저장하고, 늦은 변경만 반영한다")
    void copyStatus() throws Exception {
        save(MemberFixture.kakaoMember());

        publishInTransaction(sellerChanged(SellerStatus.ACTIVE, APPROVED_AT));
        AsyncWait.until(TIMEOUT, () -> sellerState() != null && sellerState().status() == SellerStatus.ACTIVE);
        publishInTransaction(sellerChanged(SellerStatus.SUSPENDED, APPROVED_AT.plusDays(1)));
        AsyncWait.until(TIMEOUT, () -> sellerState().status() == SellerStatus.SUSPENDED);

        var sent = sendChanged(sellerChanged(SellerStatus.ACTIVE, APPROVED_AT));

        awaitProcessed(sent);
        assertThat(sellerState()).isEqualTo(new SellerState(SellerStatus.SUSPENDED, APPROVED_AT.plusDays(1)));
    }

    @Test
    @DisplayName("활성 판매자가 정지되면 Kafka로 받아 이미 발급된 Access Token을 무효화하고, 새로 발급한 토큰에는 SELLER가 없다")
    void suspendedSellerRevokesAccessToken() {
        save(MemberFixture.withSellerState(SellerStatus.ACTIVE));

        publishInTransaction(sellerChanged(SellerStatus.SUSPENDED, APPROVED_AT.plusDays(1)));

        AsyncWait.until(TIMEOUT, () -> tokensRevokedCount() > 0);
        assertThat(sellerState().status()).isEqualTo(SellerStatus.SUSPENDED);
        assertThat(rolesOfNewToken()).doesNotContain(Role.SELLER);
    }

    @Test
    @DisplayName("판매자로 승인되면 새로 발급한 토큰에 SELLER가 담기고, Access Token은 무효화하지 않는다")
    void approvalDoesNotRevoke() throws Exception {
        save(MemberFixture.kakaoMember());

        var sent = sendChanged(sellerChanged(SellerStatus.ACTIVE, APPROVED_AT));

        awaitProcessed(sent);
        assertThat(sellerState().status()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(rolesOfNewToken()).contains(Role.SELLER);
        assertThat(tokensRevokedCountAfterMarker()).isZero();
    }

    @Test
    @DisplayName("무시되는 오래된 이벤트로는 Access Token을 무효화하지 않는다")
    void staleEventDoesNotRevoke() throws Exception {
        save(MemberFixture.withSellerState(SellerStatus.ACTIVE));

        var sent = sendChanged(sellerChanged(SellerStatus.SUSPENDED, MemberFixture.NOW.minusDays(1)));

        awaitProcessed(sent);
        assertThat(sellerState().status()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(tokensRevokedCountAfterMarker()).isZero();
    }

    @Test
    @DisplayName("이미 정지된 판매자에게 정지 이벤트가 다시 와도 Access Token을 무효화하지 않는다")
    void alreadySuspendedDoesNotRevoke() throws Exception {
        save(MemberFixture.withSellerState(SellerStatus.SUSPENDED));

        var sent = sendChanged(sellerChanged(SellerStatus.SUSPENDED, APPROVED_AT));

        awaitProcessed(sent);
        assertThat(tokensRevokedCountAfterMarker()).isZero();
    }

    @Test
    @DisplayName("같은 정지 이벤트를 두 번 받아도 결과는 한 번 받은 것과 같다(다시 무효화하지 않는다)")
    void duplicateEvent() throws Exception {
        save(MemberFixture.withSellerState(SellerStatus.ACTIVE));
        var event = sellerChanged(SellerStatus.SUSPENDED, APPROVED_AT.plusDays(1));
        sendChanged(event);
        AsyncWait.until(TIMEOUT, () -> tokensRevokedCount() > 0);

        var second = sendChanged(event);

        awaitProcessed(second);
        assertThat(sellerState()).isEqualTo(new SellerState(SellerStatus.SUSPENDED, APPROVED_AT.plusDays(1)));
        assertThat(tokensRevokedCountAfterMarker()).isEqualTo(1);
    }

    @Test
    @DisplayName("회원이 받지 않는 종류는 건너뛰고 DLT로 보내지 않으며, 뒤 메시지를 처리한다")
    void skipOtherEventType() throws Exception {
        save(MemberFixture.kakaoMember());
        sendRaw("seller.registered", UUID.randomUUID(), "받지 않는 종류라 읽지 않는 본문");

        // 키(회원 ID)가 같아 같은 파티션이므로, 뒤 메시지를 처리했으면 앞 메시지는 이미 처리됐다
        publishInTransaction(sellerChanged(SellerStatus.ACTIVE, APPROVED_AT));

        AsyncWait.until(TIMEOUT, () -> sellerState() != null && sellerState().status() == SellerStatus.ACTIVE);
        assertThat(deadLetters()).isEmpty();
    }

    @Test
    @DisplayName("받는 종류인데 본문을 읽지 못하면 재시도 없이 DLT로 보내고, 로그에 상호명을 남기지 않는다")
    void deadLetterUnreadableBody(CapturedOutput output) throws Exception {
        var businessName = "상호-" + UUID.randomUUID();
        var body = "{\"data\":{\"sellerId\":\"숫자 아님\",\"businessName\":\"" + businessName
            + "\",\"status\":\"ACTIVE\"}}";

        var sent = sendRaw("seller.changed", UUID.randomUUID(), body);

        assertDeadLetteredWithoutRetry(sent, body);
        assertThat(output.getOut()).contains("key=" + memberId).doesNotContain(businessName);
    }

    @Test
    @DisplayName("없는 회원의 판매자 이벤트는 다시 해도 같으므로 재시도 없이 DLT로 보낸다")
    void deadLetterUnknownMember() throws Exception {
        var event = sellerChanged(SellerStatus.ACTIVE, APPROVED_AT);

        var sent = sendChanged(event);

        assertDeadLetteredWithoutRetry(sent, jsonMapper.writeValueAsString(event));
    }

    private void assertDeadLetteredWithoutRetry(RecordMetadata sent, String body) {
        AsyncWait.until(TIMEOUT, () -> !deadLetters().isEmpty());
        assertThat(deadLetters()).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.key()).isEqualTo(String.valueOf(memberId));
            assertThat(deadLetter.value()).isEqualTo(body);
            assertThat(deadLetter.timestamp() - sent.timestamp()).isLessThan(RETRY_BACKOFF_TOTAL_MS);
        });
    }

    // 회원 ID는 자동 증가라 다음 값을 이 테스트의 ID로 맞춘 뒤 저장한다. 테이블 비우기가 ID를 1부터 다시 시작하게 돌려놓는다
    private void save(Member member) {
        jdbcTemplate.queryForObject("SELECT setval('member.member_id_seq', ?, false)", Long.class, memberId);
        assertThat(memberRepository.save(member).getId()).isEqualTo(memberId);
    }

    private SellerState sellerState() {
        return memberRepository.getById(memberId).getSellerState();
    }

    // 지금까지 받은 회원 이벤트 중 이 테스트 회원 키의 member.tokens-revoked 수
    private long tokensRevokedCount() {
        memberEventConsumer.poll(Duration.ofMillis(200)).forEach(memberEvents::add);
        return memberEvents.stream()
            .filter(record -> String.valueOf(memberId).equals(record.key()))
            .filter(record -> "member.tokens-revoked".equals(header(record, "eventType")))
            .count();
    }

    // 무효화하지 않았음을 보려면 기다릴 기준이 필요하다. 회원 이벤트는 한 스레드가 받은 순서대로 보내고 같은 키는 같은 파티션이라,
    // 처리가 끝난 뒤 같은 회원 키로 보낸 확인용 member.changed가 왔으면 그 앞의 무효화도 이미 와 있다
    private long tokensRevokedCountAfterMarker() {
        var marker = new MemberChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new MemberChangedEvent.Data(memberId, "확인용닉네임"));
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(marker));
        AsyncWait.until(TIMEOUT, () -> {
            memberEventConsumer.poll(Duration.ofMillis(200)).forEach(memberEvents::add);
            return memberEvents.stream()
                .anyMatch(record -> marker.eventId().toString().equals(header(record, "eventId")));
        });
        return tokensRevokedCount();
    }

    private List<Role> rolesOfNewToken() {
        var token = authTokenService.issueAccessToken(memberRepository.getById(memberId));
        return IssuedAccessToken.decode(token).roles();
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), UTF_8);
    }

    private void publishInTransaction(SellerChangedEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private SellerChangedEvent sellerChanged(SellerStatus status, LocalDateTime occurredAt) {
        return new SellerChangedEvent(UUID.randomUUID(), occurredAt,
            new SellerChangedEvent.Data(memberId, "소소한옷장", status));
    }

    // 보낸 결과를 위치로 기다려야 하는 변경은 직접 넣는다. 리스너는 본문에서 eventId · occurredAt · data만 읽는다
    private RecordMetadata sendChanged(SellerChangedEvent event) throws Exception {
        return sendRaw("seller.changed", event.eventId(), jsonMapper.writeValueAsString(event));
    }

    // 판매자가 보내지 않는 메시지(다른 종류 · 깨진 본문)를 이 테스트의 회원 ID 키로 직접 넣는다
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

    // 결과가 바뀌지 않는 메시지는 처리했는지 결과로 알 수 없다. 한 건씩 처리 완료를 커밋하므로, 회원 그룹의 커밋 위치가 그 메시지를 지나면 처리한 것이다
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
            throw new IllegalStateException("회원 그룹의 커밋 위치를 읽지 못했습니다", e);
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
