package com.bbcc.kidly.boundedcontext.childprofile;

import static com.bbcc.kidly.boundedcontext.childprofile.ChildProfileFixture.NOW;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.bbcc.kidly.boundedcontext.childprofile.out.kafka.KafkaChildProfileEventSender;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.event.DomainEvent;
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
 * 전송은 자녀 프로필 전용 스레드에서 한다. 검증 전에 destroy()로 남은 전송을 끝낸다.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class KafkaChildProfileEventSenderTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final Long CHILD_ID = 3L;
    private static final Long MEMBER_ID = 1L;
    private static final ChildChangedEvent CHANGED = changed(false);

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @DisplayName("kidly.childprofile.events에 자녀 ID 키로, eventType · eventId 헤더를 붙여 보낸다")
    void sendToChildProfileTopic() {
        var record = sendOne(CHANGED);

        assertThat(record.topic()).isEqualTo("kidly.childprofile.events");
        assertThat(record.key()).isEqualTo(String.valueOf(CHILD_ID));
        assertThat(header(record, "eventType")).isEqualTo("child.changed");
        assertThat(header(record, "eventId")).isEqualTo(CHANGED.eventId().toString());
        assertThat(record.headers().lastHeader("__TypeId__")).isNull();
    }

    @Test
    @DisplayName("본문은 공통 칸 7개와 data다. ID는 숫자, 시각은 시간대 없는 ISO 문자열이다")
    void body() {
        JsonNode body = JSON_MAPPER.readTree(sendOne(CHANGED).value());

        assertThat(body.propertyNames()).containsExactly("eventId", "eventType", "schemaVersion", "occurredAt",
            "producer", "aggregateId", "correlationId", "data");
        assertThat(body.get("eventId").asString()).isEqualTo(CHANGED.eventId().toString());
        assertThat(body.get("eventType").asString()).isEqualTo("child.changed");
        assertThat(body.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(body.get("occurredAt").asString()).isEqualTo("2026-10-13T09:00:00");
        assertThat(body.get("producer").asString()).isEqualTo("childprofile");
        assertThat(body.get("aggregateId").asString()).isEqualTo(String.valueOf(CHILD_ID));
        assertThat(body.get("correlationId").asString()).isEqualTo(String.valueOf(CHILD_ID));
        assertThat(body.get("data").get("childId").isNumber()).isTrue();
        assertThat(body.get("data").get("childId").asLong()).isEqualTo(CHILD_ID);
        assertThat(body.get("data").get("heightCm").isNumber()).isTrue();
        assertThat(body.get("data").get("heightCm").asDouble()).isEqualTo(115.0);
        assertThat(body.get("data").get("deleted").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("삭제 이벤트도 자녀 ID 키로 보내고 data.deleted가 true다")
    void deletedKey() {
        var record = sendOne(changed(true));

        assertThat(record.key()).isEqualTo(String.valueOf(CHILD_ID));
        assertThat(header(record, "eventType")).isEqualTo("child.changed");
        assertThat(JSON_MAPPER.readTree(record.value()).get("data").get("deleted").asBoolean()).isTrue();
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

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> sender.send(CHANGED));

        brokerDown.countDown();
        sender.destroy();
        then(kafkaTemplate).should().send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("받은 순서대로 보낸다")
    @SuppressWarnings("unchecked")
    void keepsOrder() {
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(CompletableFuture.completedFuture(null));
        List<DomainEvent> events = List.of(CHANGED, changed(true), CHANGED);
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
        sender.send(CHANGED);
        sender.send(CHANGED);

        sender.destroy();

        then(kafkaTemplate).should(times(2)).send(any(ProducerRecord.class));
        then(kafkaTemplate).should().destroy();
    }

    @Test
    @DisplayName("전송 대기열에 넣지 못하면(종료 중) 예외 없이 본문 없는 ERROR 로그를 남긴다")
    void rejected(CapturedOutput output) {
        var closed = Executors.newSingleThreadExecutor();
        closed.shutdown();

        assertThatCode(() -> sender(closed).send(CHANGED)).doesNotThrowAnyException();

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
        sender.send(changed(true));
        sender.send(changed(true));

        assertThatCode(() -> sender.send(CHANGED)).doesNotThrowAnyException();

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

        assertThatCode(() -> sender.send(CHANGED)).doesNotThrowAnyException();
        sender.destroy();

        assertFailureLogged(output);
    }

    @Test
    @DisplayName("보낸 뒤 실패해도 본문 없이 ERROR 로그를 남긴다")
    void logAsyncFailure(CapturedOutput output) {
        given(kafkaTemplate.send(any(ProducerRecord.class)))
            .willReturn(CompletableFuture.failedFuture(new KafkaProducerException(
                new ProducerRecord<>("kidly.childprofile.events", "3", "{\"heightCm\":115.0}"), "Failed to send",
                new TimeoutException("Topic kidly.childprofile.events not present in metadata after 3000 ms."))));
        var sender = sender(Executors.newSingleThreadExecutor());

        sender.send(CHANGED);
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

    private KafkaChildProfileEventSender sender(ExecutorService executor) {
        return new KafkaChildProfileEventSender(kafkaTemplate, JSON_MAPPER, executor);
    }

    private static ChildChangedEvent changed(boolean deleted) {
        return new ChildChangedEvent(UUID.randomUUID(), NOW, new ChildChangedEvent.Data(
            CHILD_ID, MEMBER_ID, ChildProfileFixture.HEIGHT, ChildProfileFixture.WEIGHT, Gender.MALE,
            ChildProfileFixture.BIRTH_DATE, BodyType.NORMAL, PreferredFit.LOOSE, deleted));
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), UTF_8);
    }

    // 키 · 몸무게 등(개인정보)이 로그에 남지 않아야 한다
    private static void assertFailureLogged(CapturedOutput output) {
        assertThat(output.getOut())
            .contains("ERROR")
            .contains("eventType=child.changed")
            .contains("eventId=" + CHANGED.eventId())
            .contains("key=" + CHILD_ID)
            .doesNotContain("heightCm")
            .doesNotContain(CHANGED.data().toString());
    }
}
