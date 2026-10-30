package com.bbcc.kidly.boundedcontext.order.in.event;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.app.cart.CartService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import com.bbcc.kidly.shared.product.domain.StockDeductionFailureCode;
import com.bbcc.kidly.shared.product.event.StockDeductionFailedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * 주문이 kidly.payment.events의 payment.completed · payment.failed를 Kafka로 받는다(#594). 테스트가 보내는 쪽(결제) 규칙대로
 * 메시지를 직접 넣는다(Spring 이벤트를 발행하지 않으므로 Kafka로만 들어온다). 리스너는 이 테스트 안에서만 켜고, 켠 뒤 쌓여 있던
 * 다른 테스트의 결제 메시지를 다 처리한 다음 주문을 만든다(주문 ID가 1부터 다시 시작해도 섞이지 않게).
 * 처리 실패는 한 주문의 배송 INSERT만 막는 CHECK 제약(NOT VALID)으로 만든다(배송 유니크 위반 = 진 쪽과 다르게 보관된다).
 */
@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class OrderPaymentEventKafkaIntegrationTest {

    private static final String TOPIC = "kidly.payment.events";
    private static final String PRODUCT_TOPIC = "kidly.product.events";
    private static final Long OTHER_SELLER_ID = 3L;
    private static final String BREAK_DELIVERY = "test_break_kafka_delivery_insert";
    private static final String BREAK_RECOVERY = "test_break_kafka_recovery_insert";
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
    private CartService cartService;

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
    private MessageListenerContainer productListener;

    @BeforeEach
    void startListener() {
        kafkaClient = new OrderKafkaTestClient(kafka, jsonMapper);
        listener = kafkaClient.start(guard, PaymentEventKafkaListener.ID, TOPIC);
        recorder.clear();
        saveReplicas();
    }

    @AfterEach
    void stopListener() {
        listener.stop();
        if (productListener != null) {
            productListener.stop();
        }
        jdbcTemplate.execute("alter table orders.delivery drop constraint if exists " + BREAK_DELIVERY);
        jdbcTemplate.execute("alter table orders.payment_recovery drop constraint if exists " + BREAK_RECOVERY);
        jdbcTemplate.execute("drop trigger if exists test_block_cart_delete_once on orders.cart_item");
        jdbcTemplate.execute("drop function if exists orders.test_block_cart_delete_once()");
        jdbcTemplate.execute("drop sequence if exists orders.test_cart_delete_seq");
    }

    @Test
    @DisplayName("payment.completed를 Kafka로 받으면 Spring 이벤트로 받은 주문과 같이 결제완료 · 판매자별 배송 · 장바구니 정리")
    void completedSameAsSpring() {
        OrderDetail spring = placeWithCart(4_001L);
        OrderDetail kafka = placeWithCart(4_002L);
        eventDelivery.deliver(paymentCompleted(spring, 4_001L));

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, paymentCompleted(kafka, 4_002L)));

        assertThat(outcomeOf(kafka, 4_002L)).isEqualTo(outcomeOf(spring, 4_001L))
            .containsEntry("status", "PAID").containsEntry("deliveries", 2L).containsEntry("cartLines", 0L);
    }

    @Test
    @DisplayName("payment.failed를 Kafka로 받으면 Spring 이벤트로 받은 주문과 같이 결제 실패 취소 · 취소 이벤트 1")
    void failedSameAsSpring() {
        OrderDetail spring = placeWithCart(4_011L);
        OrderDetail kafka = placeWithCart(4_012L);
        eventDelivery.deliver(paymentFailed(spring, 4_011L));

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, paymentFailed(kafka, 4_012L)));

        Order cancelled = orderRepository.findById(kafka.orderId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(Order.Status.CANCELLED);
        assertThat(cancelled.getCancelReason()).isEqualTo(Order.CancelReason.PAYMENT_FAILED);
        assertThat(outcomeOf(kafka, 4_012L)).isEqualTo(outcomeOf(spring, 4_011L));
    }

    @Test
    @DisplayName("같은 payment.completed를 두 번 받아도 결제완료 이력 1 · 배송 2 · DLT 없음")
    void duplicateAppliedOnce() {
        OrderDetail order = placeWithCart(4_021L);
        var event = paymentCompleted(order, 4_021L);

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));

        assertThat(outcomeOf(order, 4_021L)).containsEntry("status", "PAID").containsEntry("paidHistory", 1L)
            .containsEntry("deliveries", 2L);
        assertThat(kafkaClient.deadLetters(TOPIC, event.eventId().toString())).isEmpty();
    }

    @Test
    @DisplayName("결제 완료(결제 토픽)와 재고 부족 취소(상품 토픽)가 동시에 와도 하나로 수렴하고, 취소면 환불 확인 WARN은 한 줄")
    void completedAndStockFailedConcurrently(CapturedOutput output) {
        productListener = kafkaClient.start(guard, ProductEventKafkaListener.ID, PRODUCT_TOPIC);
        // 상품 토픽에 쌓여 있던 다른 테스트의 product.changed가 만든 복사본을 지우고 다시 둔다
        jdbcTemplate.update("delete from orders.replica_product");
        saveReplicas();
        for (int round = 1; round <= 3; round++) {
            Long buyerId = 4_100L + round;
            OrderDetail order = placeWithCart(buyerId);
            Long firstItemId = order.items().getFirst().orderItemId();
            var stockFailed = new StockDeductionFailedEvent(UUID.randomUUID(), LocalDateTime.now(),
                new StockDeductionFailedEvent.Data(order.orderId(), StockDeductionFailureCode.OUT_OF_STOCK,
                    List.of(new StockDeductionFailedEvent.Failure(firstItemId,
                        StockDeductionFailureCode.OUT_OF_STOCK))));

            RecordMetadata stock = kafkaClient.send(PRODUCT_TOPIC, stockFailed);
            RecordMetadata paid = kafkaClient.send(TOPIC, paymentCompleted(order, buyerId));
            kafkaClient.awaitProcessed(stock);
            kafkaClient.awaitProcessed(paid);

            Map<String, Object> outcome = outcomeOf(order, buyerId);
            if ("PAID".equals(outcome.get("status"))) {
                assertThat(outcome).containsEntry("deliveries", 2L).containsEntry("cancelledEvents", 0L);
            } else {
                assertThat(outcome).containsEntry("status", "CANCELLED").containsEntry("deliveries", 0L)
                    .containsEntry("cancelledEvents", 1L);
                assertThat(orderRepository.findById(order.orderId()).orElseThrow().getCancelReason())
                    .isEqualTo(Order.CancelReason.OUT_OF_STOCK);
                assertThat(refundCheckWarns(output, order.orderId())).as("환불 확인 WARN(%d회차)", round).isEqualTo(1);
            }
            assertThat(recoveryCountOf(order.orderId())).as("진 쪽은 보관하지 않음(%d회차)", round).isZero();
        }
    }

    @Test
    @DisplayName("결제 완료 반영이 실패하면 payment_recovery에 남기고 처리 완료(DLT 없음), 같은 키의 다음 메시지를 처리한다")
    void processingFailureKept() {
        OrderDetail order = placeWithCart(4_031L);
        breakDeliveries(order);
        var first = paymentCompleted(order, 4_031L);

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, first));

        assertThat(recoveryCountOf(order.orderId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select status from orders.payment_recovery where order_id = ?",
            String.class, order.orderId())).isEqualTo("PENDING");
        assertThat(kafkaClient.deadLetters(TOPIC, first.eventId().toString())).isEmpty();
        assertThat(outcomeOf(order, 4_031L)).containsEntry("status", "PENDING_PAYMENT");

        jdbcTemplate.execute("alter table orders.delivery drop constraint " + BREAK_DELIVERY);
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, paymentFailed(order, 4_031L)));

        assertThat(outcomeOf(order, 4_031L)).containsEntry("status", "CANCELLED");
    }

    @Test
    @DisplayName("반영 중 장바구니 줄 충돌(낙관적 락)로 통째로 되돌려지면 처리 완료로 닫지 않고, 다시 처리해 결제완료가 된다(#594)")
    void cartConflictRollbackIsRetried() {
        OrderDetail order = placeWithCart(4_061L);
        blockFirstCartDelete();
        var event = paymentCompleted(order, 4_061L);

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));

        // 트리거가 실제로 첫 DELETE를 막았고(1) 다시 처리에서 남은 DELETE가 지나갔다(2 이상) — 충돌 없이 통과한 것이 아니다
        assertThat(jdbcTemplate.queryForObject("select last_value from orders.test_cart_delete_seq", Long.class))
            .isGreaterThanOrEqualTo(2L);
        assertThat(outcomeOf(order, 4_061L)).containsEntry("status", "PAID").containsEntry("deliveries", 2L)
            .containsEntry("cartLines", 0L).containsEntry("paidHistory", 1L);
        assertThat(recoveryCountOf(order.orderId())).as("진 쪽은 보관하지 않음").isZero();
        assertThat(kafkaClient.deadLetters(TOPIC, event.eventId().toString())).isEmpty();
    }

    @Test
    @DisplayName("반영도 보관도 실패하면 재시도한 뒤 DLT에 1건 보내고 보관 기록은 없다")
    void keepFailureToDeadLetter() {
        OrderDetail order = placeWithCart(4_041L);
        breakDeliveries(order);
        jdbcTemplate.execute("alter table orders.payment_recovery add constraint " + BREAK_RECOVERY
            + " check (order_id <> " + order.orderId() + ") not valid");
        var event = paymentCompleted(order, 4_041L);

        RecordMetadata sent = kafkaClient.send(TOPIC, event);

        String eventId = event.eventId().toString();
        AsyncWait.until(OrderKafkaTestClient.TIMEOUT, () -> !kafkaClient.deadLetters(TOPIC, eventId).isEmpty());
        assertThat(kafkaClient.deadLetters(TOPIC, eventId)).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.partition()).isZero();
            assertThat(deadLetter.key()).isEqualTo(String.valueOf(order.orderId()));
            assertThat(deadLetter.timestamp() - sent.timestamp()).isGreaterThanOrEqualTo(RETRY_BACKOFF_TOTAL_MS);
        });
        assertThat(recoveryCountOf(order.orderId())).isZero();
    }

    @Test
    @DisplayName("모르는 실패 사유는 재시도 없이 DLT에 1건 보내고 주문은 그대로, 로그에 본문 값은 없다")
    void unknownEnumToDeadLetter(CapturedOutput output) {
        OrderDetail order = placeWithCart(4_051L);
        String marker = "NEW_REASON_" + UUID.randomUUID().toString().replace("-", "");
        var event = paymentFailed(order, 4_051L);
        Map<String, Object> body = kafkaClient.envelopeFields(event);
        body.put("data", Map.of("orderId", order.orderId(), "memberId", 4_051L, "requestedAmount", 55_000L,
            "reasonCode", marker, "failedAt", "2026-10-30T09:00:00"));
        String json = kafkaClient.toJson(body);
        String eventId = event.eventId().toString();

        RecordMetadata sent = kafkaClient.send(TOPIC, event.correlationId(), event.eventType(), eventId, json);

        AsyncWait.until(OrderKafkaTestClient.TIMEOUT, () -> !kafkaClient.deadLetters(TOPIC, eventId).isEmpty());
        assertThat(kafkaClient.deadLetters(TOPIC, eventId)).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.partition()).isZero();
            assertThat(deadLetter.value()).isEqualTo(json);
            assertThat(deadLetter.timestamp() - sent.timestamp()).isLessThan(RETRY_BACKOFF_TOTAL_MS);
        });
        assertThat(outcomeOf(order, 4_051L)).containsEntry("status", "PENDING_PAYMENT");
        assertThat(output.getOut()).contains("eventId=" + eventId).doesNotContain(marker);
    }

    // 장바구니 줄 첫 DELETE 한 번만 막는다(트리거가 행을 지우지 않으면 버전 확인 DELETE가 0행 → 낙관적 락 예외).
    // 처리 트랜잭션이 되돌려져도 시퀀스 값은 돌아가지 않으므로 다시 처리할 때는 막지 않는다
    private void blockFirstCartDelete() {
        jdbcTemplate.execute("create sequence orders.test_cart_delete_seq");
        jdbcTemplate.execute("""
            create function orders.test_block_cart_delete_once() returns trigger language plpgsql as $$
            begin
              if nextval('orders.test_cart_delete_seq') = 1 then
                return null;
              end if;
              return old;
            end $$""");
        jdbcTemplate.execute("create trigger test_block_cart_delete_once before delete on orders.cart_item "
            + "for each row execute function orders.test_block_cart_delete_once()");
    }

    private void saveReplicas() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(new ReplicaProduct.Values(
            2L, 2L, OTHER_SELLER_ID, "상품 B", null, "120", 30_000L, 2L, "ON_SALE", null, false, OCCURRED_AT)));
    }

    // NOT VALID라 기존 행 검사 없이 걸린다. 그 주문의 배송 INSERT만 실패한다(판매자 유니크 위반이 아니라 진 쪽으로 보지 않음)
    private void breakDeliveries(OrderDetail order) {
        jdbcTemplate.execute("alter table orders.delivery add constraint " + BREAK_DELIVERY
            + " check (order_id <> " + order.orderId() + ") not valid");
    }

    // 결과 비교용: 주문 상태 · 판매자별 배송 수 · 장바구니 줄 · 결제완료 이력 · 취소 이벤트 수
    private Map<String, Object> outcomeOf(OrderDetail order, Long buyerId) {
        Long orderId = order.orderId();
        return Map.of(
            "status", orderRepository.findById(orderId).orElseThrow().getStatus().name(),
            "deliveries", jdbcTemplate.queryForObject("select count(*) from orders.delivery where order_id = ?",
                Long.class, orderId),
            "cartLines", jdbcTemplate.queryForObject("select count(*) from orders.cart_item where member_id = ?",
                Long.class, buyerId),
            "paidHistory", jdbcTemplate.queryForObject("select count(*) from orders.order_status_history "
                + "where order_id = ? and target_type = 'ORDER' and to_status = 'PAID'", Long.class, orderId),
            "cancelledEvents", recorder.events(OrderCancelledEvent.class).stream()
                .filter(event -> event.data().orderId().equals(orderId)).count());
    }

    private OrderDetail placeWithCart(Long buyerId) {
        cartService.addItem(buyerId, 1L, null, 1);
        cartService.addItem(buyerId, 2L, null, 1);
        return orderService.place(buyerId, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L), new PlaceOrderCommand.Item(2L, null, 1, 30_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
    }

    private static PaymentCompletedEvent paymentCompleted(OrderDetail order, Long buyerId) {
        return new PaymentCompletedEvent(UUID.randomUUID(), LocalDateTime.now(), new PaymentCompletedEvent.Data(
            500L, order.orderId(), buyerId, order.totalAmount(),
            order.items().stream()
                .map(item -> new PaymentCompletedEvent.Item(item.orderItemId(), item.amount()))
                .toList(),
            LocalDateTime.now()));
    }

    private static PaymentFailedEvent paymentFailed(OrderDetail order, Long buyerId) {
        LocalDateTime now = LocalDateTime.now();
        return new PaymentFailedEvent(UUID.randomUUID(), now, new PaymentFailedEvent.Data(null, order.orderId(),
            buyerId, order.totalAmount(), PaymentFailureCode.INSUFFICIENT_BALANCE, now));
    }

    private long recoveryCountOf(Long orderId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from orders.payment_recovery where order_id = ?", Long.class, orderId);
    }

    // 취소된 주문에 온 결제 완료의 환불 확인 WARN(paid-after-cancel) 줄 수
    private static long refundCheckWarns(CapturedOutput output, Long orderId) {
        return output.getAll().lines()
            .filter(line -> line.contains(" WARN ")
                && line.contains("reason=paid-after-cancel orderId=" + orderId + " "))
            .count();
    }
}
