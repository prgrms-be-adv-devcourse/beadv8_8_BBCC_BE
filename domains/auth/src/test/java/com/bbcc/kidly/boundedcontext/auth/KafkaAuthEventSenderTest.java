package com.bbcc.kidly.boundedcontext.auth;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.bbcc.kidly.boundedcontext.auth.out.kafka.KafkaAuthEventSender;
import com.bbcc.kidly.shared.auth.PrincipalType;
import com.bbcc.kidly.shared.auth.event.AuthTokensRevokedEvent;
import java.time.Duration;
import java.time.LocalDateTime;
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
 * 전송은 인증 전용 스레드에서 한다. 검증 전에 destroy()로 남은 전송을 끝낸다.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class KafkaAuthEventSenderTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final Long MEMBER_ID = 7L;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 12, 9, 0);
    private static final LocalDateTime REVOKED_AT = LocalDateTime.of(2026, 10, 12, 9, 0, 5);
    private static final AuthTokensRevokedEvent REVOKED = revoked();

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @DisplayName("kidly.auth.events에 주체 ID 키로, eventType · eventId 헤더를 붙여 보낸다")
    void sendToAuthTopic() {
        var record = sendOne(REVOKED);

        assertThat(record.topic()).isEqualTo("kidly.auth.events");
        assertThat(record.key()).isEqualTo(String.valueOf(MEMBER_ID));
        assertThat(header(record, "eventType")).isEqualTo("auth.tokens-revoked");
        assertThat(header(record, "eventId")).isEqualTo(REVOKED.eventId().toString());
        assertThat(record.headers().lastHeader("__TypeId__")).isNull();
    }

    @Test
    @DisplayName("본문은 공통 칸 7개와 data다. data는 주체 종류 · 주체 ID 숫자 · 시간대 없는 ISO 기준 시각이다")
    void body() {
        JsonNode body = JSON_MAPPER.readTree(sendOne(REVOKED).value());

        assertThat(body.propertyNames()).containsExactly("eventId", "eventType", "schemaVersion", "occurredAt",
            "producer", "aggregateId", "correlationId", "data");
        assertThat(body.get("eventId").asString()).isEqualTo(REVOKED.eventId().toString());
        assertThat(body.get("eventType").asString()).isEqualTo("auth.tokens-revoked");
        assertThat(body.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(body.get("occurredAt").asString()).isEqualTo("2026-10-12T09:00:00");
        assertThat(body.get("producer").asString()).isEqualTo("auth");
        assertThat(body.get("aggregateId").asString()).isEqualTo(String.valueOf(MEMBER_ID));
        assertThat(body.get("correlationId").asString()).isEqualTo(String.valueOf(MEMBER_ID));
        JsonNode data = body.get("data");
        assertThat(data.propertyNames()).containsExactly("principalType", "principalId", "revokedAt");
        assertThat(data.get("principalType").asString()).isEqualTo("MEMBER");
        assertThat(data.get("principalId").isNumber()).isTrue();
        assertThat(data.get("principalId").asLong()).isEqualTo(MEMBER_ID);
        assertThat(data.get("revokedAt").asString()).isEqualTo("2026-10-12T09:00:05");
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

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> sender.send(REVOKED));

        brokerDown.countDown();
        sender.destroy();
        then(kafkaTemplate).should().send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("받은 순서대로 보낸다")
    @SuppressWarnings("unchecked")
    void keepsOrder() {
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(CompletableFuture.completedFuture(null));
        var events = List.of(revoked(), revoked(), revoked());
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
        sender.send(REVOKED);
        sender.send(REVOKED);

        sender.destroy();

        then(kafkaTemplate).should(times(2)).send(any(ProducerRecord.class));
        then(kafkaTemplate).should().destroy();
    }

    @Test
    @DisplayName("전송 대기열에 넣지 못하면(종료 중) 예외 없이 본문 없는 ERROR 로그를 남긴다")
    void rejected(CapturedOutput output) {
        var closed = Executors.newSingleThreadExecutor();
        closed.shutdown();

        assertThatCode(() -> sender(closed).send(REVOKED)).doesNotThrowAnyException();

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
        sender.send(revoked());
        sender.send(revoked());

        assertThatCode(() -> sender.send(REVOKED)).doesNotThrowAnyException();

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

        assertThatCode(() -> sender.send(REVOKED)).doesNotThrowAnyException();
        sender.destroy();

        assertFailureLogged(output);
    }

    @Test
    @DisplayName("보낸 뒤 실패해도 본문 없이 ERROR 로그를 남긴다")
    void logAsyncFailure(CapturedOutput output) {
        given(kafkaTemplate.send(any(ProducerRecord.class)))
            .willReturn(CompletableFuture.failedFuture(new KafkaProducerException(
                new ProducerRecord<>("kidly.auth.events", "7", "본문"), "Failed to send",
                new TimeoutException("Topic kidly.auth.events not present in metadata after 3000 ms."))));
        var sender = sender(Executors.newSingleThreadExecutor());

        sender.send(REVOKED);
        sender.destroy();

        assertFailureLogged(output);
        assertThat(output.getOut()).contains("not present in metadata");
    }

    @Test
    @DisplayName("앱이 내려갈 때 남은 전송을 기다리지 못하면 못 보낸 수를 ERROR로 남기고 템플릿을 닫는다")
    void logDroppedOnDestroyTimeout(CapturedOutput output) throws Exception {
        var executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()) {
            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) {
                return false;
            }
        };
        var started = new CountDownLatch(1);
        var brokerDown = new CountDownLatch(1);
        given(kafkaTemplate.send(any(ProducerRecord.class))).willAnswer(invocation -> {
            started.countDown();
            brokerDown.await();
            return CompletableFuture.completedFuture(null);
        });
        var sender = sender(executor);
        sender.send(REVOKED);
        started.await();
        sender.send(revoked());

        sender.destroy();

        assertThat(output.getOut()).contains("ERROR").contains("count=1");
        then(kafkaTemplate).should().destroy();
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, String> sendOne(AuthTokensRevokedEvent event) {
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(CompletableFuture.completedFuture(null));
        var sender = sender(Executors.newSingleThreadExecutor());

        sender.send(event);
        sender.destroy();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        then(kafkaTemplate).should().send(captor.capture());
        return captor.getValue();
    }

    private KafkaAuthEventSender sender(ExecutorService executor) {
        return new KafkaAuthEventSender(kafkaTemplate, JSON_MAPPER, executor);
    }

    private static AuthTokensRevokedEvent revoked() {
        return new AuthTokensRevokedEvent(UUID.randomUUID(), OCCURRED_AT,
            new AuthTokensRevokedEvent.Data(PrincipalType.MEMBER, MEMBER_ID, REVOKED_AT));
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), UTF_8);
    }

    private static void assertFailureLogged(CapturedOutput output) {
        assertThat(output.getOut())
            .contains("ERROR")
            .contains("eventType=auth.tokens-revoked")
            .contains("eventId=" + REVOKED.eventId())
            .contains("key=" + MEMBER_ID)
            .doesNotContain(REVOKED.data().toString());
    }
}
