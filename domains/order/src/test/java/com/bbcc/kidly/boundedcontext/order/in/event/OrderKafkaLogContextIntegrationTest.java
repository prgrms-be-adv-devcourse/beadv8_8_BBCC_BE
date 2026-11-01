package com.bbcc.kidly.boundedcontext.order.in.event;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.CapturedLogs;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.AsyncWait;
import com.bbcc.kidly.boundedcontext.support.KafkaIntegrationTest;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문 Kafka 받기 로그의 추적 값(#603): 메시지 하나를 처리하는 동안 남는 로그의 MDC에 eventId · eventType · correlationId(키)와
 * traceId(= correlationId, 요청 밖이라 비어 있음)가 붙는다. 로그 설정은 그대로 두고 로그 이벤트의 MDC를 본다(CapturedLogs).
 * 에러 처리기의 "DLT로 보냅니다" 로그와 DLT 리스너 로그까지 같은 값이 붙는지(범위)도 본다.
 */
@KafkaIntegrationTest
class OrderKafkaLogContextIntegrationTest {

    private static final String PRODUCT_TOPIC = "kidly.product.events";
    private static final String PAYMENT_TOPIC = "kidly.payment.events";
    private static final String PAYMENT_DLT = "kidly.payment.events.order.dlt";
    private static final List<String> DEAD_LETTER_TOPICS = List.of("kidly.product.events.order.dlt",
        "kidly.seller.events.order.dlt", "kidly.childprofile.events.order.dlt", PAYMENT_DLT);
    private static final String HIDDEN_TABLE = "order_dead_letter_hidden_603";
    private static final Long BUYER_ID = 6_600L;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private OrderKafkaListenerTestGuard guard;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private OrderKafkaTestClient kafkaClient;
    private CapturedLogs logs;
    private final List<MessageListenerContainer> listeners = new ArrayList<>();

    @BeforeEach
    void setUp() {
        kafkaClient = new OrderKafkaTestClient(kafka, jsonMapper);
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        logs = CapturedLogs.start();
    }

    @AfterEach
    void tearDown() {
        listeners.forEach(MessageListenerContainer::stop);
        logs.close();
        restoreTable();
    }

    @Test
    @DisplayName("재고 차감됨을 받아 처리하는 동안 남는 로그에 eventId · eventType · correlationId(주문 ID) · traceId(= 주문 ID)가 붙는다")
    void processingLogHasContext() {
        start(ProductEventKafkaListener.ID, PRODUCT_TOPIC);
        OrderDetail order = place();
        var event = stockDeducted(order.orderId());

        kafkaClient.awaitProcessed(kafkaClient.send(PRODUCT_TOPIC, event));

        assertThat(logs.mdcOf("결제 요청: orderId=" + order.orderId())).hasValueSatisfying(mdc ->
            assertThat(mdc).containsAllEntriesOf(Map.of("eventId", event.eventId().toString(),
                "eventType", "stock.deducted", "correlationId", String.valueOf(order.orderId()),
                "traceId", String.valueOf(order.orderId()))));
    }

    @Test
    @DisplayName("같은 소비 스레드가 연달아 받은 두 메시지의 로그는 각자 값만 갖는다(앞 메시지 값이 남지 않음)")
    void consecutiveMessagesDoNotMix() {
        start(ProductEventKafkaListener.ID, PRODUCT_TOPIC);
        OrderDetail first = place();
        OrderDetail second = place();
        var firstEvent = stockDeducted(first.orderId());
        var secondEvent = stockDeducted(second.orderId());

        kafkaClient.awaitProcessed(kafkaClient.send(PRODUCT_TOPIC, firstEvent));
        kafkaClient.awaitProcessed(kafkaClient.send(PRODUCT_TOPIC, secondEvent));

        assertThat(logs.mdcOf("결제 요청: orderId=" + first.orderId()).orElseThrow())
            .containsEntry("eventId", firstEvent.eventId().toString())
            .containsEntry("correlationId", String.valueOf(first.orderId()));
        assertThat(logs.mdcOf("결제 요청: orderId=" + second.orderId()).orElseThrow())
            .containsEntry("eventId", secondEvent.eventId().toString())
            .containsEntry("correlationId", String.valueOf(second.orderId()));
    }

    @Test
    @DisplayName("범위: 읽지 못한 메시지를 에러 처리기가 DLT로 보낼 때의 로그, DLT 리스너의 저장 실패 로그에도 원래 메시지 값이 붙는다")
    void errorHandlerAndDeadLetterLogsHaveContext() {
        start(PaymentEventKafkaListener.ID, PAYMENT_TOPIC);
        start(OrderDeadLetterKafkaListener.ID, PAYMENT_DLT);
        DEAD_LETTER_TOPICS.forEach(kafkaClient::awaitCaughtUp);
        jdbcTemplate.execute("alter table orders.order_dead_letter rename to " + HIDDEN_TABLE);
        var event = new PaymentFailedEvent(UUID.randomUUID(), LocalDateTime.now(), new PaymentFailedEvent.Data(
            null, 9_201L, BUYER_ID, 25_000L, PaymentFailureCode.WALLET_NOT_FOUND, LocalDateTime.now()));
        String body = kafkaClient.envelope(event).replace("\"WALLET_NOT_FOUND\"", "\"NEW_FAILURE_CODE\"");
        String eventId = event.eventId().toString();
        Map<String, String> expected = Map.of("eventId", eventId, "eventType", "payment.failed",
            "correlationId", "9201", "traceId", "9201");

        RecordMetadata sent = kafkaClient.send(PAYMENT_TOPIC, "9201", "payment.failed", eventId, body);

        AsyncWait.until(OrderKafkaTestClient.TIMEOUT,
            () -> logs.mdcOf("DLT 메시지를 저장하지 못했습니다", "eventId=" + eventId).isPresent());
        assertThat(logs.mdcOf("처리하지 못한 이벤트를 DLT로 보냅니다", "eventId=" + eventId).orElseThrow())
            .as("에러 처리기의 DLT 보냄 로그(offset %d)", sent.offset()).containsAllEntriesOf(expected);
        assertThat(logs.mdcOf("DLT 메시지를 저장하지 못했습니다", "eventId=" + eventId).orElseThrow())
            .as("DLT 리스너 로그(DLT로 복사된 원래 헤더 · 키)").containsAllEntriesOf(expected);
        restoreTable();
        AsyncWait.until(OrderKafkaTestClient.TIMEOUT, () -> jdbcTemplate.queryForObject(
            "select count(*) from orders.order_dead_letter where event_id = ?", Long.class, eventId) > 0);
    }

    private void start(String listenerId, String topic) {
        listeners.add(kafkaClient.start(guard, listenerId, topic));
    }

    private void restoreTable() {
        Long hidden = jdbcTemplate.queryForObject("select count(*) from pg_tables where schemaname = 'orders' "
            + "and tablename = ?", Long.class, HIDDEN_TABLE);
        if (hidden > 0) {
            jdbcTemplate.execute("alter table orders." + HIDDEN_TABLE + " rename to order_dead_letter");
        }
    }

    private OrderDetail place() {
        return orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
    }

    private static StockDeductedEvent stockDeducted(Long orderId) {
        return new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(), new StockDeductedEvent.Data(orderId));
    }
}
