package com.bbcc.kidly.boundedcontext.seller;

import static com.bbcc.kidly.boundedcontext.seller.SellerFixture.BUSINESS_NAME;
import static com.bbcc.kidly.boundedcontext.seller.SellerFixture.NOW;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.bbcc.kidly.boundedcontext.seller.out.kafka.KafkaSellerEventSender;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
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
 * 전송은 판매자 전용 스레드에서 한다. 검증 전에 destroy()로 남은 전송을 끝낸다.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class KafkaSellerEventSenderTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final Long SELLER_ID = 7L;
    private static final SellerChangedEvent CHANGED = new SellerChangedEvent(UUID.randomUUID(), NOW,
        new SellerChangedEvent.Data(SELLER_ID, BUSINESS_NAME, SellerStatus.ACTIVE));

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @DisplayName("kidly.seller.events에 판매자 ID 키로, eventType · eventId 헤더를 붙여 보낸다")
    void sendToSellerTopic() {
        var record = sendOne(CHANGED);

        assertThat(record.topic()).isEqualTo("kidly.seller.events");
        assertThat(record.key()).isEqualTo(String.valueOf(SELLER_ID));
        assertThat(header(record, "eventType")).isEqualTo("seller.changed");
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
        assertThat(body.get("eventType").asString()).isEqualTo("seller.changed");
        assertThat(body.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(body.get("occurredAt").asString()).isEqualTo("2026-10-13T09:00:00");
        assertThat(body.get("producer").asString()).isEqualTo("seller");
        assertThat(body.get("aggregateId").asString()).isEqualTo(String.valueOf(SELLER_ID));
        assertThat(body.get("correlationId").asString()).isEqualTo(String.valueOf(SELLER_ID));
        assertThat(body.get("data").get("sellerId").isNumber()).isTrue();
        assertThat(body.get("data").get("sellerId").asLong()).isEqualTo(SELLER_ID);
        assertThat(body.get("data").get("status").asString()).isEqualTo("ACTIVE");
        assertThat(body.get("data").get("businessName").asString()).isEqualTo(BUSINESS_NAME);
    }

    @Test
    @DisplayName("정지 이벤트도 판매자 ID 키로 보낸다")
    void suspendedKey() {
        var record = sendOne(suspended());

        assertThat(record.key()).isEqualTo(String.valueOf(SELLER_ID));
        assertThat(header(record, "eventType")).isEqualTo("seller.changed");
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
        List<DomainEvent> events = List.of(CHANGED, suspended(), CHANGED);
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
        sender.send(suspended());
        sender.send(suspended());

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
                new ProducerRecord<>("kidly.seller.events", "7", BUSINESS_NAME), "Failed to send",
                new TimeoutException("Topic kidly.seller.events not present in metadata after 3000 ms."))));
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

    private KafkaSellerEventSender sender(ExecutorService executor) {
        return new KafkaSellerEventSender(kafkaTemplate, JSON_MAPPER, executor);
    }

    private static SellerChangedEvent suspended() {
        return new SellerChangedEvent(UUID.randomUUID(), NOW,
            new SellerChangedEvent.Data(SELLER_ID, BUSINESS_NAME, SellerStatus.SUSPENDED));
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), UTF_8);
    }

    // 상호명 등 본문이 로그에 남지 않아야 한다
    private static void assertFailureLogged(CapturedOutput output) {
        assertThat(output.getOut())
            .contains("ERROR")
            .contains("eventType=seller.changed")
            .contains("eventId=" + CHANGED.eventId())
            .contains("key=" + SELLER_ID)
            .doesNotContain(BUSINESS_NAME)
            .doesNotContain(CHANGED.data().toString());
    }
}
