package com.bbcc.kidly.boundedcontext.member;

import static com.bbcc.kidly.boundedcontext.member.MemberFixture.EMAIL;
import static com.bbcc.kidly.boundedcontext.member.MemberFixture.NOW;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.bbcc.kidly.boundedcontext.member.out.kafka.KafkaMemberEventSender;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import com.bbcc.kidly.shared.member.event.MemberTokensRevokedEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaProducerException;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 전송은 회원 전용 스레드에서 한다. 검증 전에 destroy()로 남은 전송을 끝낸다.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class KafkaMemberEventSenderTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final Long MEMBER_ID = 7L;
    private static final MemberRegisteredEvent REGISTERED = new MemberRegisteredEvent(UUID.randomUUID(), NOW,
        new MemberRegisteredEvent.Data(MEMBER_ID, EMAIL));

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @DisplayName("kidly.member.events에 회원 ID 키로, eventType · eventId 헤더를 붙여 보낸다")
    void sendToMemberTopic() {
        var record = sendOne(REGISTERED);

        assertThat(record.topic()).isEqualTo("kidly.member.events");
        assertThat(record.key()).isEqualTo(String.valueOf(MEMBER_ID));
        assertThat(header(record, "eventType")).isEqualTo("member.registered");
        assertThat(header(record, "eventId")).isEqualTo(REGISTERED.eventId().toString());
        assertThat(record.headers().lastHeader("__TypeId__")).isNull();
    }

    @Test
    @DisplayName("본문은 공통 칸 7개와 data다. ID는 숫자, 시각은 시간대 없는 ISO 문자열이다")
    void body() {
        JsonNode body = JSON_MAPPER.readTree(sendOne(REGISTERED).value());

        assertThat(body.propertyNames()).containsExactly("eventId", "eventType", "schemaVersion", "occurredAt",
            "producer", "aggregateId", "correlationId", "data");
        assertThat(body.get("eventId").asString()).isEqualTo(REGISTERED.eventId().toString());
        assertThat(body.get("eventType").asString()).isEqualTo("member.registered");
        assertThat(body.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(body.get("occurredAt").asString()).isEqualTo("2026-10-12T09:00:00");
        assertThat(body.get("producer").asString()).isEqualTo("member");
        assertThat(body.get("aggregateId").asString()).isEqualTo(String.valueOf(MEMBER_ID));
        assertThat(body.get("correlationId").asString()).isEqualTo(String.valueOf(MEMBER_ID));
        assertThat(body.get("data").get("memberId").isNumber()).isTrue();
        assertThat(body.get("data").get("memberId").asLong()).isEqualTo(MEMBER_ID);
        assertThat(body.get("data").get("email").asString()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("탈퇴 이벤트도 회원 ID 키로 보낸다")
    void withdrawnKey() {
        var record = sendOne(withdrawn());

        assertThat(record.key()).isEqualTo(String.valueOf(MEMBER_ID));
        assertThat(header(record, "eventType")).isEqualTo("member.withdrawn");
    }

    @Test
    @DisplayName("토큰 무효화의 data는 회원 ID 숫자와 기준 시각(시간대 없는 ISO 문자열)이다")
    void tokensRevokedData() {
        var event = new MemberTokensRevokedEvent(UUID.randomUUID(), NOW,
            new MemberTokensRevokedEvent.Data(MEMBER_ID, NOW));

        var record = sendOne(event);

        assertThat(record.key()).isEqualTo(String.valueOf(MEMBER_ID));
        assertThat(header(record, "eventType")).isEqualTo("member.tokens-revoked");
        JsonNode data = JSON_MAPPER.readTree(record.value()).get("data");
        assertThat(data.propertyNames()).containsExactly("memberId", "revokedAt");
        assertThat(data.get("memberId").isNumber()).isTrue();
        assertThat(data.get("revokedAt").asString()).isEqualTo("2026-10-12T09:00:00");
    }

    @Test
    @DisplayName("브로커가 응답하지 않아도 부른 쪽(커밋 뒤 요청 스레드)은 기다리지 않는다")
    void doesNotBlockCaller() {
        var brokerDown = new CountDownLatch(1);
        given(kafkaTemplate.send(any(ProducerRecord.class))).willAnswer(invocation -> {
            brokerDown.await();
            return CompletableFuture.completedFuture(null);
        });
        var sender = sender(Executors.newSingleThreadExecutor());

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> sender.send(REGISTERED));

        brokerDown.countDown();
        sender.destroy();
        then(kafkaTemplate).should().send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("받은 순서대로 보낸다")
    @SuppressWarnings("unchecked")
    void keepsOrder() {
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(CompletableFuture.completedFuture(null));
        List<DomainEvent> events = List.of(REGISTERED, withdrawn(), REGISTERED);
        var sender = sender(Executors.newSingleThreadExecutor());

        events.forEach(sender::send);
        sender.destroy();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        then(kafkaTemplate).should(times(3)).send(captor.capture());
        assertThat(captor.getAllValues()).extracting(record -> header(record, "eventId"))
            .containsExactlyElementsOf(events.stream().map(event -> event.eventId().toString()).toList());
    }

    @Test
    @DisplayName("앱이 내려갈 때 남은 전송을 마치고 템플릿을 닫는다")
    void drainOnDestroy() {
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(CompletableFuture.completedFuture(null));
        var sender = sender(Executors.newSingleThreadExecutor());
        sender.send(REGISTERED);
        sender.send(REGISTERED);

        sender.destroy();

        then(kafkaTemplate).should(times(2)).send(any(ProducerRecord.class));
        then(kafkaTemplate).should().destroy();
    }

    @Test
    @DisplayName("전송 대기열에 넣지 못하면(종료 중) 예외 없이 본문 없는 ERROR 로그를 남긴다")
    void rejected(CapturedOutput output) {
        var closed = Executors.newSingleThreadExecutor();
        closed.shutdown();

        assertThatCode(() -> sender(closed).send(REGISTERED)).doesNotThrowAnyException();

        then(kafkaTemplate).should(never()).send(any(ProducerRecord.class));
        assertFailureLogged(output);
    }

    @Test
    @DisplayName("대기열이 가득 차면 넘친 이벤트는 버리고 ERROR 로그를 남긴다. 이미 받은 것은 보낸다")
    void queueFull(CapturedOutput output) {
        var brokerDown = new CountDownLatch(1);
        given(kafkaTemplate.send(any(ProducerRecord.class))).willAnswer(invocation -> {
            brokerDown.await();
            return CompletableFuture.completedFuture(null);
        });
        // 운영과 같은 모양(스레드 1 · 크기 제한 대기열)에 대기열만 1칸으로 줄인다
        var oneSlot = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(1));
        var sender = sender(oneSlot);
        sender.send(withdrawn());
        sender.send(withdrawn());

        assertThatCode(() -> sender.send(REGISTERED)).doesNotThrowAnyException();

        assertFailureLogged(output);
        brokerDown.countDown();
        sender.destroy();
        then(kafkaTemplate).should(times(2)).send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("보내기 전에 실패해도 본문 없이 ERROR 로그를 남긴다")
    void logImmediateFailure(CapturedOutput output) {
        given(kafkaTemplate.send(any(ProducerRecord.class))).willThrow(new KafkaException("브로커 없음"));
        var sender = sender(Executors.newSingleThreadExecutor());

        assertThatCode(() -> sender.send(REGISTERED)).doesNotThrowAnyException();
        sender.destroy();

        assertFailureLogged(output);
    }

    @Test
    @DisplayName("보낸 뒤 실패해도 본문 없이 ERROR 로그를 남긴다")
    void logAsyncFailure(CapturedOutput output) {
        given(kafkaTemplate.send(any(ProducerRecord.class)))
            .willReturn(CompletableFuture.failedFuture(new KafkaProducerException(
                new ProducerRecord<>("kidly.member.events", "7", EMAIL), "Failed to send",
                new TimeoutException("Topic kidly.member.events not present in metadata after 3000 ms."))));
        var sender = sender(Executors.newSingleThreadExecutor());

        sender.send(REGISTERED);
        sender.destroy();

        assertFailureLogged(output);
        assertThat(output.getOut()).contains("not present in metadata");
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, String> sendOne(DomainEvent event) {
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(CompletableFuture.completedFuture(null));
        var sender = sender(Executors.newSingleThreadExecutor());

        sender.send(event);
        sender.destroy();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        then(kafkaTemplate).should().send(captor.capture());
        return captor.getValue();
    }

    private KafkaMemberEventSender sender(ExecutorService executor) {
        return new KafkaMemberEventSender(kafkaTemplate, JSON_MAPPER, executor);
    }

    private static MemberWithdrawnEvent withdrawn() {
        return new MemberWithdrawnEvent(UUID.randomUUID(), NOW, new MemberWithdrawnEvent.Data(MEMBER_ID));
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), UTF_8);
    }

    // 이메일(개인정보)이 로그에 남지 않아야 한다
    private static void assertFailureLogged(CapturedOutput output) {
        assertThat(output.getOut())
            .contains("ERROR")
            .contains("eventType=member.registered")
            .contains("eventId=" + REGISTERED.eventId())
            .contains("key=" + MEMBER_ID)
            .doesNotContain(EMAIL)
            .doesNotContain(REGISTERED.data().toString());
    }
}
