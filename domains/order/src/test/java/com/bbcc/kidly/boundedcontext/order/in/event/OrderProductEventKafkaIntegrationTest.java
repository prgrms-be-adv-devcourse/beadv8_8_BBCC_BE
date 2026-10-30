package com.bbcc.kidly.boundedcontext.order.in.event;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.domain.StockDeductionFailureCode;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductionFailedEvent;
import com.bbcc.kidly.boundedcontext.order.support.AsyncWait;
import com.bbcc.kidly.boundedcontext.order.support.KafkaIntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문이 kidly.product.events의 product.changed · stock.deducted · stock.deduction-failed를 Kafka로 받는다(#541).
 * 테스트가 보내는 쪽 규칙대로 메시지를 직접 넣는다(Spring 이벤트를 발행하지 않으므로 Kafka로만 들어온다).
 * 주문은 주문 서비스로 만든다: 상품 쪽에 그 옵션이 없어 상품 리스너는 재고 이벤트를 보내지 않으므로 재고 결과는 이 테스트가 넣은 것뿐이다.
 * 리스너를 켠 뒤 쌓여 있던 메시지(다른 테스트의 상품 · 재고 이벤트)를 다 처리한 다음 주문 복사본을 비우고 데이터를 만든다.
 * 처리 실패는 OrderEventFailureIntegrationTest와 같이 한 주문 행만 막는 CHECK 제약(NOT VALID)으로 만든다.
 */
@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class OrderProductEventKafkaIntegrationTest {

    private static final String TOPIC = "kidly.product.events";
    private static final String BREAK_ORDER = "test_break_kafka_order_update";
    private static final String BREAK_FAILURE = "test_break_kafka_failure_insert";
    private static final long RETRY_BACKOFF_TOTAL_MS = 3_000;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private OrderKafkaListenerTestGuard guard;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private OrderFlowEventRecorder recorder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private OrderKafkaTestClient kafkaClient;
    private MessageListenerContainer listener;

    @BeforeEach
    void startListener() {
        kafkaClient = new OrderKafkaTestClient(kafka, jsonMapper);
        listener = kafkaClient.start(guard, ProductEventKafkaListener.ID, TOPIC);
        // 쌓여 있던 다른 테스트의 product.changed가 만든 복사본(같은 옵션 ID일 수 있음)을 지우고 시작한다
        jdbcTemplate.update("delete from orders.replica_product");
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        recorder.clear();
    }

    @AfterEach
    void stopListener() {
        listener.stop();
        jdbcTemplate.execute("alter table orders.orders drop constraint if exists " + BREAK_ORDER);
        jdbcTemplate.execute("alter table orders.order_event_failure drop constraint if exists " + BREAK_FAILURE);
    }

    @Test
    @DisplayName("product.changed를 Kafka로 받은 상품 복사본이 같은 값을 Spring 이벤트로 받은 것과 같다")
    void productChangedSameAsSpringEvent() {
        long springProductId = ThreadLocalRandom.current().nextLong(1_000_000, 2_000_000);
        long kafkaProductId = springProductId + 1_000_000;
        eventDelivery.deliver(productChanged(springProductId, springProductId * 10));

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, productChanged(kafkaProductId, kafkaProductId * 10)));

        assertThat(storedOption(kafkaProductId * 10)).isNotNull().isEqualTo(storedOption(springProductId * 10));
    }

    @Test
    @DisplayName("stock.deducted를 Kafka로 받으면 Spring 이벤트로 받은 주문과 같이 결제를 한 번 요청한다")
    void stockDeductedSameAsSpringEvent() {
        OrderDetail spring = place();
        OrderDetail kafka = place();
        eventDelivery.deliver(stockDeducted(spring));

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, stockDeducted(kafka)));

        assertThat(paymentRequests(kafka)).isEqualTo(1).isEqualTo(paymentRequests(spring));
        assertThat(outboxTypes(kafka)).contains("order.payment-requested").isEqualTo(outboxTypes(spring));
        assertThat(statusOf(kafka)).isEqualTo(statusOf(spring));
    }

    @Test
    @DisplayName("stock.deduction-failed를 Kafka로 받으면 Spring 이벤트로 받은 주문과 같이 재고 부족으로 취소한다")
    void stockDeductionFailedSameAsSpringEvent() {
        OrderDetail spring = place();
        OrderDetail kafka = place();
        eventDelivery.deliver(stockDeductionFailed(spring, StockDeductionFailureCode.OUT_OF_STOCK));

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC,
            stockDeductionFailed(kafka, StockDeductionFailureCode.OUT_OF_STOCK)));

        Order cancelled = orderRepository.findById(kafka.orderId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(Order.Status.CANCELLED);
        assertThat(cancelled.getCancelReason()).isEqualTo(Order.CancelReason.OUT_OF_STOCK);
        assertThat(statusOf(kafka)).isEqualTo(statusOf(spring));
        assertThat(outboxTypes(kafka)).contains("order.cancelled").isEqualTo(outboxTypes(spring));
    }

    @Test
    @DisplayName("같은 stock.deducted를 두 번 받아도 결제 요청은 한 번이고 DLT로 보내지 않는다")
    void duplicateAppliedOnce() {
        OrderDetail order = place();
        var event = stockDeducted(order);

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));

        assertThat(paymentRequests(order)).isEqualTo(1);
        assertThat(outboxTypes(order)).containsOnlyOnce("order.payment-requested");
        assertThat(kafkaClient.deadLetters(TOPIC, event.eventId().toString())).isEmpty();
    }

    @Test
    @DisplayName("주문이 받지 않는 종류는 건너뛰고, 모르는 필드가 더 있는 같은 키의 다음 메시지를 처리한다")
    void skipsOtherTypeAndIgnoresUnknownFields() {
        OrderDetail order = place();
        String key = String.valueOf(order.orderId());
        String skippedId = OrderKafkaTestClient.newEventId();
        kafkaClient.send(TOPIC, key, "stock.restored", skippedId, "주문이 읽지 않는 본문");
        var event = stockDeducted(order);
        Map<String, Object> body = kafkaClient.envelopeFields(event);
        body.put("traceId", "새 공통 칸");
        body.put("data", Map.of("orderId", order.orderId(), "newField", "새 값"));

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, key, event.eventType(), event.eventId().toString(),
            kafkaClient.toJson(body)));

        assertThat(paymentRequests(order)).isEqualTo(1);
        assertThat(kafkaClient.deadLetters(TOPIC, skippedId)).isEmpty();
        assertThat(kafkaClient.deadLetters(TOPIC, event.eventId().toString())).isEmpty();
    }

    @Test
    @DisplayName("모르는 재고 실패 사유는 재시도 없이 DLT에 1건 보내고 주문은 그대로, 로그에 본문 값은 없다")
    void unknownEnumToDeadLetter(CapturedOutput output) {
        OrderDetail order = place();
        String marker = "NEW_REASON_" + UUID.randomUUID().toString().replace("-", "");
        var event = stockDeductionFailed(order, StockDeductionFailureCode.OUT_OF_STOCK);
        Map<String, Object> body = kafkaClient.envelopeFields(event);
        body.put("data", Map.of("orderId", order.orderId(), "reason", marker, "failures", List.of()));
        String json = kafkaClient.toJson(body);
        String eventId = event.eventId().toString();

        RecordMetadata sent = kafkaClient.send(TOPIC, event.correlationId(), event.eventType(), eventId, json);

        assertDeadLetteredWithoutRetry(sent, eventId, json);
        assertThat(statusOf(order)).isEqualTo(Order.Status.PENDING_PAYMENT);
        assertThat(output.getOut()).contains("eventId=" + eventId, "key=" + order.orderId()).doesNotContain(marker);
    }

    @Test
    @DisplayName("eventType 헤더가 없으면 재시도 없이 DLT에 1건 보낸다")
    void missingEventTypeToDeadLetter() {
        String eventId = OrderKafkaTestClient.newEventId();
        String key = String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE));

        RecordMetadata sent = kafkaClient.send(TOPIC, key, null, eventId, "{}");

        assertDeadLetteredWithoutRetry(sent, eventId, "{}");
    }

    @Test
    @DisplayName("처리가 실패하면 주문 자체 보관에 남기고 처리 완료(DLT 없음), 같은 키의 다음 메시지를 처리한다")
    void processingFailureKeptAndNextProcessed() {
        OrderDetail order = place();
        breakOrder(order);
        var first = stockDeducted(order);

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, first));

        assertThat(failureRows(order)).singleElement().satisfies(failure -> {
            assertThat(failure).containsEntry("event_type", "STOCK_DEDUCTED").containsEntry("status", "PENDING");
            assertThat(failure).containsEntry("attempt_count", 1);
        });
        assertThat(kafkaClient.deadLetters(TOPIC, first.eventId().toString())).isEmpty();
        assertThat(statusOf(order)).isEqualTo(Order.Status.PENDING_PAYMENT);

        jdbcTemplate.execute("alter table orders.orders drop constraint " + BREAK_ORDER);
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, stockDeducted(order)));

        assertThat(paymentRequests(order)).isEqualTo(1);
    }

    @Test
    @DisplayName("보관까지 실패하면 재시도한 뒤 DLT에 1건 보내고 보관 기록은 없다")
    void keepFailureAlsoFailsToDeadLetter() {
        OrderDetail order = place();
        breakOrder(order);
        jdbcTemplate.execute("alter table orders.order_event_failure add constraint " + BREAK_FAILURE
            + " check (order_id <> " + order.orderId() + ") not valid");
        var event = stockDeducted(order);

        RecordMetadata sent = kafkaClient.send(TOPIC, event);

        String eventId = event.eventId().toString();
        AsyncWait.until(OrderKafkaTestClient.TIMEOUT, () -> !kafkaClient.deadLetters(TOPIC, eventId).isEmpty());
        assertThat(kafkaClient.deadLetters(TOPIC, eventId)).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.partition()).isZero();
            assertThat(deadLetter.key()).isEqualTo(String.valueOf(order.orderId()));
            assertThat(deadLetter.timestamp() - sent.timestamp()).isGreaterThanOrEqualTo(RETRY_BACKOFF_TOTAL_MS);
        });
        assertThat(failureRows(order)).isEmpty();
        assertThat(statusOf(order)).isEqualTo(Order.Status.PENDING_PAYMENT);
    }

    private void assertDeadLetteredWithoutRetry(RecordMetadata sent, String eventId, String body) {
        AsyncWait.until(OrderKafkaTestClient.TIMEOUT, () -> !kafkaClient.deadLetters(TOPIC, eventId).isEmpty());
        assertThat(kafkaClient.deadLetters(TOPIC, eventId)).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.partition()).isZero();
            assertThat(deadLetter.value()).isEqualTo(body);
            assertThat(deadLetter.timestamp() - sent.timestamp()).isLessThan(RETRY_BACKOFF_TOTAL_MS);
        });
    }

    // NOT VALID라 기존 행 검사 없이 걸린다. 그 주문 행을 바꾸는 UPDATE만 실패한다
    private void breakOrder(OrderDetail order) {
        jdbcTemplate.execute("alter table orders.orders add constraint " + BREAK_ORDER
            + " check (id <> " + order.orderId() + ") not valid");
    }

    private OrderDetail place() {
        return orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
    }

    private Order.Status statusOf(OrderDetail order) {
        return orderRepository.findById(order.orderId()).orElseThrow().getStatus();
    }

    private long paymentRequests(OrderDetail order) {
        return recorder.events(OrderPaymentRequestedEvent.class).stream()
            .filter(event -> event.data().orderId().equals(order.orderId()))
            .count();
    }

    private List<String> outboxTypes(OrderDetail order) {
        return jdbcTemplate.queryForList("select event_type from orders.order_outbox where message_key = ? "
            + "and event_type <> 'order.created' order by id", String.class, String.valueOf(order.orderId()));
    }

    private List<Map<String, Object>> failureRows(OrderDetail order) {
        return jdbcTemplate.queryForList("select event_type, status, attempt_count from orders.order_event_failure "
            + "where order_id = ?", order.orderId());
    }

    private Map<String, Object> storedOption(long optionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select seller_id, category_id, product_status, "
            + "product_name, option_name, size, unit_price, is_sold_out, option_sale_status, event_occurred_at "
            + "from orders.replica_product where option_id = ?", optionId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static ProductChangedEvent productChanged(long productId, long optionId) {
        return new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT, new ProductChangedEvent.Data(productId, 2L,
            "상품 A", 1L, ProductStatus.ON_SALE, null, OCCURRED_AT.minusDays(1), false, List.of(
                new ProductChangedEvent.Option(optionId, "색상: 네이비 / 사이즈: 110", "110", 25_000L, null, null,
                    false, OptionSaleStatus.ON_SALE))));
    }

    private static StockDeductedEvent stockDeducted(OrderDetail order) {
        return new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductedEvent.Data(order.orderId()));
    }

    private static StockDeductionFailedEvent stockDeductionFailed(OrderDetail order, StockDeductionFailureCode code) {
        return new StockDeductionFailedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductionFailedEvent.Data(order.orderId(), code, List.of(
                new StockDeductionFailedEvent.Failure(order.items().getFirst().orderItemId(), code))));
    }
}
