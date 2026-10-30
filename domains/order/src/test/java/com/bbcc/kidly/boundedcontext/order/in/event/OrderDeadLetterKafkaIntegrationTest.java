package com.bbcc.kidly.boundedcontext.order.in.event;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.deadletter.UnreadableOrderMessageException;
import com.bbcc.kidly.boundedcontext.order.support.AsyncWait;
import com.bbcc.kidly.boundedcontext.order.support.KafkaIntegrationTest;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.KafkaHeaders;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문 DLT 4개를 읽어 order_dead_letter에 저장한다(#601). 저장이 실패하면 DLT로 다시 보내지 않고 끝없이 다시 시도한다.
 * DLT 메시지는 DeadLetterPublishingRecoverer가 만드는 모양(원래 위치 · 예외 DLT 헤더 + 원래 헤더)으로 직접 넣거나,
 * 원래 토픽에 읽지 못하는 메시지를 넣어 실제 에러 처리기가 DLT로 보내게 한다(끝에서 끝).
 * DLT는 컨테이너 수명 동안 남아 다른 테스트의 메시지도 있으므로 원래 위치 · eventId로 골라 본다.
 */
@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class OrderDeadLetterKafkaIntegrationTest {

    private static final String PAYMENT_TOPIC = "kidly.payment.events";
    private static final String PRODUCT_TOPIC = "kidly.product.events";
    private static final List<String> DEAD_LETTER_TOPICS = List.of("kidly.product.events.order.dlt",
        "kidly.seller.events.order.dlt", "kidly.childprofile.events.order.dlt", "kidly.payment.events.order.dlt");
    private static final String PAYMENT_DLT = "kidly.payment.events.order.dlt";
    private static final String HIDDEN_TABLE = "order_dead_letter_hidden";
    private static final String BODY_MARKER = "본문-표식-DLT";
    private static final String RETRY_LOG = "DLT 메시지를 저장하지 못했습니다";
    private static final String DEAD_LETTER_LOG = "처리하지 못한 이벤트를 DLT로 보냅니다";
    // 원래 토픽 처리기는 1초 간격 처음 + 재시도 3번(약 3초) 뒤 DLT로 보낸다. 저장 재시도는 1 · 2 · 4초 간격이라 8초면 4번 실패한다
    private static final Duration FAILING_WINDOW = Duration.ofSeconds(8);

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private OrderKafkaListenerTestGuard guard;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private OrderKafkaTestClient kafkaClient;
    private MessageListenerContainer deadLetterListener;
    private MessageListenerContainer sourceListener;

    @BeforeEach
    void startListener() {
        kafkaClient = new OrderKafkaTestClient(kafka, jsonMapper);
        deadLetterListener = kafkaClient.start(guard, OrderDeadLetterKafkaListener.ID, PAYMENT_DLT);
        DEAD_LETTER_TOPICS.forEach(kafkaClient::awaitCaughtUp);
    }

    @AfterEach
    void stopListener() {
        deadLetterListener.stop();
        if (sourceListener != null) {
            sourceListener.stop();
        }
        restoreTable();
    }

    @Test
    @DisplayName("DLT 메시지를 받으면 원래 위치 · 키 · 헤더 · 본문 공통 칸 · 관련 주문 · 실패 유형 · 예외로 OPEN 1건 저장한다")
    void savesDeadLetter() {
        long offset = newOffset();
        String eventId = OrderKafkaTestClient.newEventId();

        kafkaClient.awaitProcessed(kafkaClient.send(deadLetter(offset, eventId,
            "org.springframework.orm.ObjectOptimisticLockingFailureException")));

        Map<String, Object> row = rowAt(offset);
        assertThat(row).containsEntry("source_topic", PAYMENT_TOPIC)
            .containsEntry("source_partition", 2)
            .containsEntry("message_key", "42")
            .containsEntry("event_type", "payment.completed")
            .containsEntry("event_id", eventId)
            .containsEntry("producer", "payment")
            .containsEntry("aggregate_id", "500")
            .containsEntry("correlation_id", "42")
            .containsEntry("order_id", 42L)
            .containsEntry("failure_type", "STORE_FAILED")
            .containsEntry("exception_class", "org.springframework.orm.ObjectOptimisticLockingFailureException")
            .containsEntry("exception_message", "낙관적 락 실패")
            .containsEntry("stack_trace_head", "trace\n\tat a")
            .containsEntry("received_count", 1)
            .containsEntry("status", "OPEN");
    }

    @Test
    @DisplayName("같은 원래 위치의 DLT 메시지가 두 번 오면 1건이고 받은 횟수 2")
    void sameSourceTwiceIsOneRow() {
        long offset = newOffset();
        String eventId = OrderKafkaTestClient.newEventId();

        kafkaClient.awaitProcessed(kafkaClient.send(deadLetter(offset, eventId, "java.lang.IllegalStateException")));
        kafkaClient.awaitProcessed(kafkaClient.send(deadLetter(offset, eventId, "java.lang.IllegalStateException")));

        assertThat(rowAt(offset)).containsEntry("received_count", 2).containsEntry("status", "OPEN");
    }

    @Test
    @DisplayName("끝에서 끝: 모르는 enum 값의 payment.failed는 재시도 없이 DLT로 가고, DLT 리스너가 원래 위치로 READ_FAILED 1건 저장한다")
    void unknownEnumEndToEnd() {
        sourceListener = kafkaClient.start(guard, PaymentEventKafkaListener.ID, PAYMENT_TOPIC);
        var event = new PaymentFailedEvent(UUID.randomUUID(), LocalDateTime.now(), new PaymentFailedEvent.Data(
            null, 9_101L, 1L, 50_000L, PaymentFailureCode.WALLET_NOT_FOUND, LocalDateTime.now()));
        String body = kafkaClient.envelope(event).replace("\"WALLET_NOT_FOUND\"", "\"NEW_FAILURE_CODE\"");
        String eventId = event.eventId().toString();

        RecordMetadata sent = kafkaClient.send(PAYMENT_TOPIC, "9101", "payment.failed", eventId, body);

        Map<String, Object> row = awaitRowOf(eventId);
        assertThat(row).containsEntry("source_topic", PAYMENT_TOPIC)
            .containsEntry("source_partition", sent.partition())
            .containsEntry("source_offset", sent.offset())
            .containsEntry("event_type", "payment.failed")
            .containsEntry("payload", body)
            .containsEntry("producer", "payment")
            .containsEntry("order_id", 9_101L)
            .containsEntry("failure_type", "READ_FAILED")
            .containsEntry("received_count", 1);
        assertThat((String) row.get("exception_class")).startsWith("tools.jackson.");
    }

    @Test
    @DisplayName("끝에서 끝: eventType 헤더가 없는 깨진 메시지는 READ_FAILED, 공통 칸 · 관련 주문은 빈칸으로 저장한다")
    void unreadableMessageEndToEnd() {
        sourceListener = kafkaClient.start(guard, ProductEventKafkaListener.ID, PRODUCT_TOPIC);
        String eventId = OrderKafkaTestClient.newEventId();

        kafkaClient.send(PRODUCT_TOPIC, "3", null, eventId, "{broken");

        Map<String, Object> row = awaitRowOf(eventId);
        assertThat(row).containsEntry("source_topic", PRODUCT_TOPIC)
            .containsEntry("event_type", null)
            .containsEntry("payload", "{broken")
            .containsEntry("producer", null)
            .containsEntry("occurred_at", null)
            .containsEntry("aggregate_id", null)
            .containsEntry("correlation_id", null)
            .containsEntry("order_id", null)
            .containsEntry("failure_type", "READ_FAILED")
            .containsEntry("exception_class", UnreadableOrderMessageException.class.getName());
    }

    @Test
    @DisplayName("저장이 실패하면(DB 장애) DLT로 다시 보내지 않고 다시 시도해, 원인이 풀리면 1건 저장한다. "
        + "같은 메시지의 실패 로그는 처음 한 줄(ERROR)뿐이고 본문 · 예외 메시지가 없다")
    void storeFailureIsRetriedNotDeadLettered(CapturedOutput output) {
        long offset = newOffset();
        String eventId = OrderKafkaTestClient.newEventId();
        // 표를 잠시 다른 이름으로 바꿔 저장이 실패하게 한다(DB 장애 흉내, 거부한 행 값이 로그에 남지 않는 실패)
        jdbcTemplate.execute("alter table orders.order_dead_letter rename to " + HIDDEN_TABLE);

        RecordMetadata sent = kafkaClient.send(deadLetter(offset, eventId, "java.lang.IllegalStateException"));

        AsyncWait.until(OrderKafkaTestClient.TIMEOUT, () -> retryLogs(output, eventId) > 0);
        long deadline = System.nanoTime() + FAILING_WINDOW.toNanos();
        while (System.nanoTime() < deadline && deadLetterLogs(output, eventId) == 0) {
            Thread.onSpinWait();
        }
        assertThat(deadLetterLogs(output, eventId)).as("DLT로 보내는 로그 없음").isZero();
        assertThat(retryLogs(output, eventId)).as("같은 메시지의 실패 로그는 처음 한 줄").isEqualTo(1);
        assertThat(output.getOut().lines().filter(line -> line.contains(RETRY_LOG)))
            .allSatisfy(line -> assertThat(line).contains(" ERROR "));
        restoreTable();

        kafkaClient.awaitProcessed(sent);

        assertThat(rowAt(offset)).containsEntry("received_count", 1).containsEntry("status", "OPEN");
        assertThat(kafkaClient.deadLetters(PAYMENT_TOPIC, eventId)).as("DLT에는 처음 넣은 1건뿐").hasSize(1);
        assertThat(topicsAfter(PAYMENT_DLT)).as("DLT의 DLT 없음").isEmpty();
        assertThat(output.getOut()).contains("eventType=payment.completed", "eventId=" + eventId, "key=42")
            .doesNotContain(BODY_MARKER, "낙관적 락 실패");
    }

    /**
     * DeadLetterPublishingRecoverer가 만드는 모양: 원래 위치(파티션 int · 오프셋 long) · 예외 DLT 헤더 + 원래 헤더 eventType · eventId.
     */
    private ProducerRecord<String, String> deadLetter(long offset, String eventId, String causeClass) {
        String body = """
            {"eventId":"%s","eventType":"payment.completed","schemaVersion":1,"occurredAt":"2026-10-31T09:30:00",
             "producer":"payment","aggregateId":"500","correlationId":"42",
             "data":{"paymentId":500,"orderId":42,"memo":"%s"}}""".formatted(eventId, BODY_MARKER);
        var record = new ProducerRecord<>(PAYMENT_DLT, 0, "42", body);
        record.headers().add(KafkaHeaders.DLT_ORIGINAL_TOPIC, PAYMENT_TOPIC.getBytes(UTF_8));
        record.headers().add(KafkaHeaders.DLT_ORIGINAL_PARTITION,
            ByteBuffer.allocate(Integer.BYTES).putInt(2).array());
        record.headers().add(KafkaHeaders.DLT_ORIGINAL_OFFSET,
            ByteBuffer.allocate(Long.BYTES).putLong(offset).array());
        record.headers().add(KafkaHeaders.DLT_EXCEPTION_FQCN,
            "org.springframework.kafka.listener.ListenerExecutionFailedException".getBytes(UTF_8));
        record.headers().add(KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN, causeClass.getBytes(UTF_8));
        record.headers().add(KafkaHeaders.DLT_EXCEPTION_MESSAGE, "낙관적 락 실패".getBytes(UTF_8));
        record.headers().add(KafkaHeaders.DLT_EXCEPTION_STACKTRACE, "trace\n\tat a".getBytes(UTF_8));
        record.headers().add("eventType", "payment.completed".getBytes(UTF_8));
        record.headers().add("eventId", eventId.getBytes(UTF_8));
        return record;
    }

    private void restoreTable() {
        Long hidden = jdbcTemplate.queryForObject("select count(*) from pg_tables where schemaname = 'orders' "
            + "and tablename = ?", Long.class, HIDDEN_TABLE);
        if (hidden > 0) {
            jdbcTemplate.execute("alter table orders." + HIDDEN_TABLE + " rename to order_dead_letter");
        }
    }

    // 다른 테스트의 행과 겹치지 않는 원래 오프셋
    private static long newOffset() {
        return ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE / 2);
    }

    private Map<String, Object> rowAt(long offset) {
        return jdbcTemplate.queryForMap("select * from orders.order_dead_letter where source_topic = ? "
            + "and source_partition = 2 and source_offset = ?", PAYMENT_TOPIC, offset);
    }

    private Map<String, Object> awaitRowOf(String eventId) {
        AsyncWait.until(OrderKafkaTestClient.TIMEOUT, () -> jdbcTemplate.queryForObject(
            "select count(*) from orders.order_dead_letter where event_id = ?", Long.class, eventId) > 0);
        return jdbcTemplate.queryForMap("select * from orders.order_dead_letter where event_id = ?", eventId);
    }

    private static long retryLogs(CapturedOutput output, String eventId) {
        return output.getOut().lines().filter(line -> line.contains(RETRY_LOG) && line.contains(eventId)).count();
    }

    private static long deadLetterLogs(CapturedOutput output, String eventId) {
        return output.getOut().lines().filter(line -> line.contains(DEAD_LETTER_LOG) && line.contains(eventId))
            .count();
    }

    private List<String> topicsAfter(String topic) {
        try (var admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafka.getBootstrapServers()))) {
            return admin.listTopics().names().get(10, TimeUnit.SECONDS).stream()
                .filter(name -> name.startsWith(topic) && !name.equals(topic))
                .toList();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
