package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.ORDER_ID;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.domain.Payout;
import com.bbcc.kidly.boundedcontext.payment.domain.PayoutRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import com.bbcc.kidly.shared.payment.domain.PayoutFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutFailedEvent;
import com.bbcc.kidly.shared.settlement.event.SettlementPayoutRequestedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 결제 이벤트가 커밋 뒤 kidly.payment.events에 규칙대로(키 · 헤더 · 본문) 도착하는지 본다(#534).
 * 받는 쪽(주문 · 정산)이 Spring 이벤트로 받는 것은 기존 테스트가 본다.
 * 다른 테스트의 결제 이벤트도 같은 토픽에 쌓이므로 헤더 eventId로 이 테스트의 메시지를 찾는다.
 */
@IntegrationTest
@RecordApplicationEvents
class PaymentKafkaPublishIntegrationTest {

    private static final String TOPIC = "kidly.payment.events";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final Long SETTLEMENT_ID = 31L;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private PayoutRepository payoutRepository;

    @Autowired
    private KafkaContainer kafka;

    private KafkaConsumer<String, String> consumer;
    private final List<ConsumerRecord<String, String>> received = new ArrayList<>();

    @BeforeEach
    void subscribe() {
        consumer = new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "payment-kafka-test-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumer.subscribe(List.of(TOPIC));
    }

    @AfterEach
    void close() {
        consumer.close();
    }

    @Test
    @DisplayName("결제가 완료되면 주문 ID 키로 보내고 헤더 eventType · eventId와 본문 8칸을 채운다")
    void paymentCompleted() {
        walletRepository.save(PaymentFixture.wallet(20_000L));
        OrderPaymentRequestedEvent requested = paymentRequested();

        publishInTransaction(requested);

        PaymentCompletedEvent completed = events.stream(PaymentCompletedEvent.class).findFirst().orElseThrow();
        ConsumerRecord<String, String> record = awaitRecord(completed);
        assertThat(record.key()).isEqualTo(String.valueOf(ORDER_ID));
        assertThat(header(record, "eventType")).isEqualTo("payment.completed");
        assertThat(record.headers().lastHeader("__TypeId__")).isNull();
        assertThat(JSON_MAPPER.readTree(record.value()).propertyNames()).containsExactly("eventId", "eventType",
            "schemaVersion", "occurredAt", "producer", "aggregateId", "correlationId", "data");
        // 주문이 발행한 결제 요청은 보내지 않는다. 키(주문 ID)가 같아 같은 파티션이므로 먼저 들어왔어야 한다
        assertThat(findRecord(requested)).isEmpty();
    }

    @Test
    @DisplayName("잔액이 부족하면 payment.failed를 주문 ID 키로 보낸다")
    void paymentFailed() {
        publishInTransaction(paymentRequested());

        PaymentFailedEvent failed = events.stream(PaymentFailedEvent.class).findFirst().orElseThrow();
        assertSent(failed, "payment.failed", ORDER_ID);
    }

    @Test
    @DisplayName("정산 입금이 끝나면 payment.payout-completed를 정산서 ID 키로 보낸다")
    void payoutCompleted() {
        publishInTransaction(payoutRequested());

        PaymentPayoutCompletedEvent completed =
            events.stream(PaymentPayoutCompletedEvent.class).findFirst().orElseThrow();
        assertSent(completed, "payment.payout-completed", SETTLEMENT_ID);
    }

    @Test
    @DisplayName("정산 입금에 실패했던 정산서는 payment.payout-failed를 정산서 ID 키로 보낸다")
    void payoutFailed() {
        saveFailedPayout();

        publishInTransaction(payoutRequested());

        PaymentPayoutFailedEvent failed = events.stream(PaymentPayoutFailedEvent.class).findFirst().orElseThrow();
        assertSent(failed, "payment.payout-failed", SETTLEMENT_ID);
    }

    @Test
    @DisplayName("롤백되면 보내지 않는다")
    void rolledBack() {
        PaymentPayoutFailedEvent rolledBack = new PaymentPayoutFailedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new PaymentPayoutFailedEvent.Data(SETTLEMENT_ID, MEMBER_ID, 270_000L, PayoutFailureCode.WALLET_NOT_FOUND,
                LocalDateTime.now()));
        saveFailedPayout();
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(rolledBack);
            status.setRollbackOnly();
        });
        publishInTransaction(payoutRequested());

        // 키(정산서 ID)가 같아 같은 파티션이므로, 뒤에 보낸 것을 받았으면 앞에서 보낸 것은 이미 왔어야 한다
        PaymentPayoutFailedEvent committed = events.stream(PaymentPayoutFailedEvent.class)
            .filter(event -> !event.eventId().equals(rolledBack.eventId()))
            .findFirst().orElseThrow();
        awaitRecord(committed);
        assertThat(findRecord(rolledBack)).isEmpty();
    }

    private void assertSent(DomainEvent event, String eventType, Long key) {
        ConsumerRecord<String, String> record = awaitRecord(event);
        assertThat(record.key()).isEqualTo(String.valueOf(key));
        assertThat(header(record, "eventType")).isEqualTo(eventType);
    }

    // 전송은 비동기라 메시지가 올 때까지 읽는다. 받은 뒤 끝내므로 다음 테스트에 끼어들지 않는다
    private ConsumerRecord<String, String> awaitRecord(DomainEvent event) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            consumer.poll(Duration.ofMillis(200)).forEach(received::add);
            Optional<ConsumerRecord<String, String>> found = findRecord(event);
            if (found.isPresent()) {
                return found.get();
            }
        }
        throw new AssertionError(TIMEOUT.toSeconds() + "초 안에 " + event.eventType() + " 메시지가 오지 않았습니다");
    }

    private Optional<ConsumerRecord<String, String>> findRecord(DomainEvent event) {
        return received.stream()
            .filter(record -> event.eventId().toString().equals(header(record, "eventId")))
            .findFirst();
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), UTF_8);
    }

    // 지급에 실패했던 정산서는 다시 요청이 오면 실패를 다시 발행한다
    private void saveFailedPayout() {
        payoutRepository.save(Payout.fail(SETTLEMENT_ID, MEMBER_ID, 270_000L, PayoutFailureCode.WALLET_NOT_FOUND,
            LocalDateTime.now()));
    }

    private void publishInTransaction(DomainEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private static OrderPaymentRequestedEvent paymentRequested() {
        return new OrderPaymentRequestedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new OrderPaymentRequestedEvent.Data(ORDER_ID, MEMBER_ID, 15_000L, List.of(
                new OrderPaymentRequestedEvent.Item(1000L, 15_000L))));
    }

    private static SettlementPayoutRequestedEvent payoutRequested() {
        return new SettlementPayoutRequestedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new SettlementPayoutRequestedEvent.Data(
                SETTLEMENT_ID, MEMBER_ID, 270_000L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
    }
}
