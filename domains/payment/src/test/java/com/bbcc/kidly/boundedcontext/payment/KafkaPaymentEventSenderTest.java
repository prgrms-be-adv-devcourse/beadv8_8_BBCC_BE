package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.NOW;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.ORDER_ID;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.bbcc.kidly.boundedcontext.payment.out.kafka.KafkaPaymentEventSender;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
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
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 전송은 결제 전용 스레드에서 한다. 검증 전에 destroy()로 남은 전송을 끝낸다.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class KafkaPaymentEventSenderTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final Long PAYMENT_ID = 1L;
    private static final Long SETTLEMENT_ID = 31L;
    private static final PaymentCompletedEvent COMPLETED = new PaymentCompletedEvent(UUID.randomUUID(), NOW,
        new PaymentCompletedEvent.Data(PAYMENT_ID, ORDER_ID, MEMBER_ID, 10_000L,
            List.of(new PaymentCompletedEvent.Item(10L, 10_000L)), NOW));

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @DisplayName("kidly.payment.events에 주문 ID 키로, eventType · eventId 헤더를 붙여 보낸다")
    void sendToPaymentTopic() {
        ProducerRecord<String, String> record = sendOne(COMPLETED);

        assertThat(record.topic()).isEqualTo("kidly.payment.events");
        assertThat(record.key()).isEqualTo(String.valueOf(ORDER_ID));
        assertThat(header(record, "eventType")).isEqualTo("payment.completed");
        assertThat(header(record, "eventId")).isEqualTo(COMPLETED.eventId().toString());
        assertThat(record.headers().lastHeader("__TypeId__")).isNull();
    }

    @Test
    @DisplayName("본문은 공통 칸 7개와 data다. ID는 숫자, 시각은 시간대 없는 ISO 문자열이다")
    void body() {
        JsonNode body = JSON_MAPPER.readTree(sendOne(COMPLETED).value());

        assertThat(body.propertyNames()).containsExactly("eventId", "eventType", "schemaVersion", "occurredAt",
            "producer", "aggregateId", "correlationId", "data");
        assertThat(body.get("eventId").asString()).isEqualTo(COMPLETED.eventId().toString());
        assertThat(body.get("eventType").asString()).isEqualTo("payment.completed");
        assertThat(body.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(body.get("occurredAt").asString()).isEqualTo("2026-10-12T12:00:00");
        assertThat(body.get("producer").asString()).isEqualTo("payment");
        assertThat(body.get("aggregateId").asString()).isEqualTo(String.valueOf(PAYMENT_ID));
        assertThat(body.get("correlationId").asString()).isEqualTo(String.valueOf(ORDER_ID));
        assertThat(body.get("data").get("orderId").isNumber()).isTrue();
        assertThat(body.get("data").get("orderId").asLong()).isEqualTo(ORDER_ID);
        assertThat(body.get("data").get("items").get(0).get("orderItemId").asLong()).isEqualTo(10L);
    }

    @Test
    @DisplayName("저장하지 않은 결제 실패(결제 ID 없음)도 주문 ID 키로 보낸다")
    void failedWithoutPaymentId() {
        PaymentFailedEvent failed = new PaymentFailedEvent(UUID.randomUUID(), NOW, new PaymentFailedEvent.Data(
            null, ORDER_ID, MEMBER_ID, 10_000L, PaymentFailureCode.AMOUNT_MISMATCH, NOW));

        assertThat(sendOne(failed).key()).isEqualTo(String.valueOf(ORDER_ID));
    }

    @Test
    @DisplayName("정산 지급 이벤트는 정산서 ID 키로 보낸다")
    void payoutKey() {
        ProducerRecord<String, String> record = sendOne(payoutCompleted());

        assertThat(record.key()).isEqualTo(String.valueOf(SETTLEMENT_ID));
        assertThat(header(record, "eventType")).isEqualTo("payment.payout-completed");
    }

    @Test
    @DisplayName("브로커가 응답하지 않아도 부른 쪽(커밋 뒤 요청 스레드)은 기다리지 않는다")
    void doesNotBlockCaller() {
        CountDownLatch brokerDown = new CountDownLatch(1);
        given(kafkaTemplate.send(any(ProducerRecord.class))).willAnswer(invocation -> {
            brokerDown.await();
            return CompletableFuture.completedFuture(null);
        });
        KafkaPaymentEventSender sender = sender(Executors.newSingleThreadExecutor());

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> sender.send(COMPLETED));

        brokerDown.countDown();
        sender.destroy();
        then(kafkaTemplate).should().send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("받은 순서대로 보낸다")
    @SuppressWarnings("unchecked")
    void keepsOrder() {
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(CompletableFuture.completedFuture(null));
        List<DomainEvent> events = List.of(COMPLETED, payoutCompleted(), COMPLETED);
        KafkaPaymentEventSender sender = sender(Executors.newSingleThreadExecutor());

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
        KafkaPaymentEventSender sender = sender(Executors.newSingleThreadExecutor());
        sender.send(COMPLETED);
        sender.send(COMPLETED);

        sender.destroy();

        then(kafkaTemplate).should(times(2)).send(any(ProducerRecord.class));
        then(kafkaTemplate).should().destroy();
    }

    @Test
    @DisplayName("전송 대기열에 넣지 못하면(가득 참 · 종료 중) 예외 없이 본문 없는 ERROR 로그를 남긴다")
    void rejected(CapturedOutput output) {
        ExecutorService closed = Executors.newSingleThreadExecutor();
        closed.shutdown();

        assertThatCode(() -> sender(closed).send(COMPLETED)).doesNotThrowAnyException();

        then(kafkaTemplate).should(never()).send(any(ProducerRecord.class));
        assertFailureLogged(output);
    }

    @Test
    @DisplayName("대기열이 가득 차면 넘친 이벤트는 버리고 ERROR 로그를 남긴다. 이미 받은 것은 보낸다")
    void queueFull(CapturedOutput output) {
        CountDownLatch brokerDown = new CountDownLatch(1);
        given(kafkaTemplate.send(any(ProducerRecord.class))).willAnswer(invocation -> {
            brokerDown.await();
            return CompletableFuture.completedFuture(null);
        });
        // 운영과 같은 모양(스레드 1 · 크기 제한 대기열)에 대기열만 1칸으로 줄인다
        ExecutorService oneSlot = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(1));
        KafkaPaymentEventSender sender = sender(oneSlot);
        PaymentPayoutCompletedEvent queued = payoutCompleted();
        sender.send(payoutCompleted());
        await(() -> ((ThreadPoolExecutor) oneSlot).getActiveCount() == 1);
        sender.send(queued);

        assertThatCode(() -> sender.send(COMPLETED)).doesNotThrowAnyException();

        assertFailureLogged(output);
        brokerDown.countDown();
        sender.destroy();
        then(kafkaTemplate).should(times(2)).send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("보내기 전에 실패해도 본문 없이 ERROR 로그를 남긴다")
    void logImmediateFailure(CapturedOutput output) {
        given(kafkaTemplate.send(any(ProducerRecord.class))).willThrow(new KafkaException("브로커 없음"));
        KafkaPaymentEventSender sender = sender(Executors.newSingleThreadExecutor());

        assertThatCode(() -> sender.send(COMPLETED)).doesNotThrowAnyException();
        sender.destroy();

        assertFailureLogged(output);
    }

    @Test
    @DisplayName("보낸 뒤 실패해도 본문 없이 ERROR 로그를 남긴다")
    void logAsyncFailure(CapturedOutput output) {
        given(kafkaTemplate.send(any(ProducerRecord.class)))
            .willReturn(CompletableFuture.failedFuture(new KafkaException("전송 시간 초과")));
        KafkaPaymentEventSender sender = sender(Executors.newSingleThreadExecutor());

        sender.send(COMPLETED);
        sender.destroy();

        assertFailureLogged(output);
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, String> sendOne(DomainEvent event) {
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(CompletableFuture.completedFuture(null));
        KafkaPaymentEventSender sender = sender(Executors.newSingleThreadExecutor());

        sender.send(event);
        sender.destroy();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        then(kafkaTemplate).should().send(captor.capture());
        return captor.getValue();
    }

    // 전송 스레드가 첫 이벤트를 잡을 때까지 기다린다(최대 1초)
    private static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("1초 안에 전송 스레드가 시작하지 않았습니다");
            }
            Thread.onSpinWait();
        }
    }

    private KafkaPaymentEventSender sender(ExecutorService executor) {
        return new KafkaPaymentEventSender(kafkaTemplate, JSON_MAPPER, executor);
    }

    private static PaymentPayoutCompletedEvent payoutCompleted() {
        return new PaymentPayoutCompletedEvent(UUID.randomUUID(), NOW,
            new PaymentPayoutCompletedEvent.Data(SETTLEMENT_ID, MEMBER_ID, 270_000L, 5L, NOW));
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), UTF_8);
    }

    private static void assertFailureLogged(CapturedOutput output) {
        assertThat(output.getOut())
            .contains("ERROR")
            .contains("eventType=payment.completed")
            .contains("eventId=" + COMPLETED.eventId())
            .contains("key=" + ORDER_ID)
            .doesNotContain("memberId")
            .doesNotContain(COMPLETED.data().toString());
    }
}
