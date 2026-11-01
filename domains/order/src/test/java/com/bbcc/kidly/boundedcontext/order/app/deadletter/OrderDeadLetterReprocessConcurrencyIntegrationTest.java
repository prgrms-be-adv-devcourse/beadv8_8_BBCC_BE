package com.bbcc.kidly.boundedcontext.order.app.deadletter;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.OrderRows;
import com.bbcc.kidly.boundedcontext.order.PaymentEvents;
import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.OrderDeadLetterDetail;
import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.ReceiveDeadLetterCommand;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.AsyncWait;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 같은 DLT 메시지를 동시에 재처리하면 한 번만 처리한다(#601). 먼저 온 재처리가 기록을 잠근 채 처리하는 동안
 * 늦은 쪽은 기다리지 않고 409(재처리 중). 처리 중임을 확실히 하려고 테스트가 주문 행을 잠가 먼저 온 재처리를 붙잡아 둔다.
 */
@IntegrationTest
class OrderDeadLetterReprocessConcurrencyIntegrationTest {

    private static final Long BUYER_ID = 6_500L;
    private static final Long ADMIN_ID = 99L;
    private static final String PAID = "ORDER PENDING_PAYMENT>PAID payment.completed";

    @Autowired
    private OrderDeadLetterService deadLetterService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private OrderRows orderRows;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("먼저 온 재처리가 처리 중이면 늦은 재처리는 기다리지 않고 409(재처리 중), 먼저 온 쪽만 PAID 1회분 · REPROCESSED")
    void concurrentReprocessOnce() throws Exception {
        OrderDetail order = placeAwaitingPayment();
        Long id = receive(PaymentEvents.completed(order, BUYER_ID));
        CountDownLatch orderLocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> lockHolder = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(
            status -> {
                jdbcTemplate.queryForList("select id from orders.orders where id = ? for update", order.orderId());
                orderLocked.countDown();
                await(release);
            }));
        assertThat(orderLocked.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<OrderDeadLetterDetail> first = CompletableFuture.supplyAsync(
            () -> deadLetterService.reprocess(id, ADMIN_ID));
        AsyncWait.until(Duration.ofSeconds(10), () -> isLocked(id));

        assertThatThrownBy(() -> deadLetterService.reprocess(id, ADMIN_ID))
            .isInstanceOfSatisfying(BusinessException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(OrderErrorCode.DEAD_LETTER_IN_PROGRESS));
        release.countDown();
        lockHolder.get(10, TimeUnit.SECONDS);

        assertThat(first.get(10, TimeUnit.SECONDS).status()).isEqualTo("REPROCESSED");
        assertThat(orderRows.historyRows(order.orderId())).filteredOn(PAID::equals).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("select reprocess_count from orders.order_dead_letter where id = ?",
            Integer.class, id)).isEqualTo(1);
    }

    // 다른 트랜잭션이 기록을 잠그고 있으면 기다리지 않는 잠금이 바로 실패한다
    private boolean isLocked(Long id) {
        try {
            transactionTemplate.executeWithoutResult(status -> jdbcTemplate.queryForList(
                "select id from orders.order_dead_letter where id = ? for update nowait", id));
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private OrderDetail placeAwaitingPayment() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        OrderDetail order = orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
        eventDelivery.deliver(new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductedEvent.Data(order.orderId())));
        return order;
    }

    private Long receive(DomainEvent event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", event.eventId());
        body.put("eventType", event.eventType());
        body.put("schemaVersion", event.schemaVersion());
        body.put("occurredAt", event.occurredAt());
        body.put("producer", event.producer());
        body.put("aggregateId", event.aggregateId());
        body.put("correlationId", event.correlationId());
        body.put("data", event.data());
        deadLetterService.receive(new ReceiveDeadLetterCommand("kidly.payment.events", 0, 11L,
            event.correlationId(), event.eventType(), event.eventId().toString(),
            jsonMapper.writeValueAsString(body), "java.lang.IllegalStateException", null, null));
        return jdbcTemplate.queryForObject("select id from orders.order_dead_letter where source_offset = 11",
            Long.class);
    }
}
