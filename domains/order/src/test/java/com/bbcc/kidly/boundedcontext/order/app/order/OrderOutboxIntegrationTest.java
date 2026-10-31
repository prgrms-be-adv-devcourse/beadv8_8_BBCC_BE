package com.bbcc.kidly.boundedcontext.order.app.order;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.PaymentEvents;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.outbox.OrderOutboxRelayService;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventOutbox;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventSender;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.in.scheduler.OrderOutboxCleanupScheduler;
import com.bbcc.kidly.boundedcontext.order.in.scheduler.OrderOutboxRelayScheduler;
import com.bbcc.kidly.boundedcontext.order.support.IntegrationTest;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문이 발행하는 이벤트가 같은 트랜잭션에서 아웃박스(orders.order_outbox)에 저장되는지(#540).
 * 업무가 커밋되면 발행한 이벤트마다 한 행, 롤백되면 행도 없다. 본문은 앱의 JsonMapper로 만든 공통 칸 7개 + data.
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class OrderOutboxIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private OrderFlowEventRecorder recorder;

    @Autowired
    private OrderEventOutbox outbox;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @BeforeEach
    void setUp() {
        recorder.clear();
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("주문하기가 커밋되면 order.created 한 행: 이벤트 ID · 종류 · 토픽 · 키(주문 ID) · 본문, 보내지 않은 상태")
    void savesCreatedInSameTransaction() {
        OrderDetail order = place();

        OrderCreatedEvent created = recorder.events(OrderCreatedEvent.class).getFirst();
        Map<String, Object> row = jdbcTemplate.queryForMap("select * from orders.order_outbox");
        assertThat(row).containsEntry("event_id", created.eventId())
            .containsEntry("event_type", "order.created")
            .containsEntry("topic", "kidly.order.events")
            .containsEntry("message_key", String.valueOf(order.orderId()))
            .containsEntry("sent_at", null)
            .containsEntry("attempt_count", 0);
        JsonNode payload = jsonMapper.readTree((String) row.get("payload"));
        assertThat(payload.propertyNames()).containsExactly("eventId", "eventType", "schemaVersion", "occurredAt",
            "producer", "aggregateId", "correlationId", "data");
        assertThat(payload.get("eventId").asString()).isEqualTo(created.eventId().toString());
        assertThat(payload.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(LocalDateTime.parse(payload.get("occurredAt").asString())).isEqualTo(created.occurredAt());
        assertThat(payload.get("producer").asString()).isEqualTo("order");
        assertThat(payload.get("aggregateId").asString()).isEqualTo(String.valueOf(order.orderId()));
        assertThat(payload.get("correlationId").asString()).isEqualTo(String.valueOf(order.orderId()));
        assertThat(payload.get("data").get("orderId").asLong()).isEqualTo(order.orderId());
        assertThat(payload.get("data").get("items").get(0).get("quantity").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("주문하기 → 재고 차감됨 → 결제 실패 취소: 발행한 이벤트 순서대로 행이 쌓인다(리스너 트랜잭션도 같은 방식)")
    void savesEveryPublishedEventInOrder() {
        OrderDetail order = place();

        eventDelivery.deliver(new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductedEvent.Data(order.orderId())));
        // 주문 모듈 테스트에는 결제 도메인이 없으므로 결제 실패를 직접 넣는다(#616)
        eventDelivery.deliver(PaymentEvents.insufficientBalance(order, BUYER_ID));

        List<UUID> published = List.of(
            recorder.events(OrderCreatedEvent.class).getFirst().eventId(),
            recorder.events(OrderPaymentRequestedEvent.class).getFirst().eventId(),
            recorder.events(OrderCancelledEvent.class).getFirst().eventId());
        assertThat(jdbcTemplate.queryForList("select event_id from orders.order_outbox order by id", UUID.class))
            .containsExactlyElementsOf(published);
        assertThat(jdbcTemplate.queryForList("select event_type from orders.order_outbox order by id", String.class))
            .containsExactly("order.created", "order.payment-requested", "order.cancelled");
    }

    @Test
    @DisplayName("주문하기를 감싼 트랜잭션이 롤백되면 주문도 아웃박스 행도 남지 않는다")
    void rollsBackWithBusiness() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            place();
            throw new IllegalStateException("업무 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.orders", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.order_outbox", Long.class)).isZero();
        assertThat(recorder.events(DomainEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("트랜잭션 밖에서 저장하면 실패한다(업무 트랜잭션에 합류만 한다)")
    void requiresTransaction() {
        OrderCreatedEvent event = new OrderCreatedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new OrderCreatedEvent.Data(1L, BUYER_ID, List.of()));

        assertThatThrownBy(() -> outbox.append(event)).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.order_outbox", Long.class)).isZero();
    }

    @Test
    @DisplayName("Kafka 보내기가 꺼진 컨텍스트에는 sender · 릴레이 · 릴레이 스케줄러가 없어도 아웃박스 저장 · 정리는 있다")
    void relayBeansAbsentWithoutKafka() {
        assertThat(context.getBeanNamesForType(OrderEventSender.class)).isEmpty();
        assertThat(context.getBeanNamesForType(OrderOutboxRelayService.class)).isEmpty();
        assertThat(context.getBeanNamesForType(OrderOutboxRelayScheduler.class)).isEmpty();
        assertThat(context.getBeanNamesForType(OrderEventOutbox.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(OrderOutboxCleanupScheduler.class)).hasSize(1);
    }

    private OrderDetail place() {
        return orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, 1L, 2, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
    }
}
