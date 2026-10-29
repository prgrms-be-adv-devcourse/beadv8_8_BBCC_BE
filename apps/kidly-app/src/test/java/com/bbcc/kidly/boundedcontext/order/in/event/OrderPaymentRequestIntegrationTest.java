package com.bbcc.kidly.boundedcontext.order.in.event;

import static com.bbcc.kidly.boundedcontext.order.OrderRows.PLACED;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.OrderRows;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * 주문하기 → order.created → (상품 도메인 대신 직접) stock.deducted → 리스너 → order.payment-requested.
 * 결제 도메인 리스너가 결제 요청을 받아 같은 흐름 안에서 바로 결제하므로(동기), 재고 차감됨 처리가 끝나면
 * 주문은 이미 결제완료(지갑 있음) 또는 결제 실패로 취소(지갑 없음) 상태다. 그 뒤 같은 재고 차감됨이 다시 와도 추가 발행이 없는지 본다.
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않는다.
 * ApplicationEvents는 테스트 스레드에서 발행된 이벤트만 기록하므로 HTTP 대신 주문하기 서비스를 직접 부른다.
 */
@IntegrationTest
@RecordApplicationEvents
class OrderPaymentRequestIntegrationTest {

    @Autowired
    private OrderRows orderRows;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @Autowired
    private WalletRepository walletRepository;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("재고 차감됨을 받으면 order.payment-requested를 계약 필드로 한 번 발행한다")
    void requestsPaymentWithContractFields() {
        OrderDetail order = place();
        OrderCreatedEvent created = single(OrderCreatedEvent.class);
        assertThat(created.data().orderId()).isEqualTo(order.orderId());

        eventDelivery.deliver(stockDeducted(order.orderId()));

        assertThat(fieldNames(OrderPaymentRequestedEvent.Data.class))
            .containsExactly("orderId", "memberId", "amount", "items");
        assertThat(fieldNames(OrderPaymentRequestedEvent.Item.class)).containsExactly("orderItemId", "amount");
        OrderPaymentRequestedEvent.Data requested = single(OrderPaymentRequestedEvent.class).data();
        assertThat(requested.orderId()).isEqualTo(order.orderId());
        assertThat(requested.memberId()).isEqualTo(BUYER_ID);
        assertThat(requested.amount()).isEqualTo(50_000L);
        assertThat(requested.items()).singleElement().satisfies(item -> {
            assertThat(item.orderItemId()).isEqualTo(created.data().items().get(0).orderItemId());
            assertThat(item.amount()).isEqualTo(50_000L);
        });
    }

    @Test
    @DisplayName("결제 실패로 취소된 뒤 같은 재고 차감됨이 다시 와도 결제 요청을 추가로 발행하지 않는다")
    void noRequestAgainAfterPaymentFailedCancel() {
        OrderDetail order = place();
        StockDeductedEvent stockDeducted = stockDeducted(order.orderId());
        eventDelivery.deliver(stockDeducted);
        assertThat(orderService.getOrder(BUYER_ID, order.orderId()).status()).isEqualTo("CANCELLED");
        long version = orderRows.versionOf(order.orderId());

        eventDelivery.deliver(stockDeducted);

        assertThat(events.stream(OrderPaymentRequestedEvent.class).toList()).hasSize(1);
        assertThat(orderService.getOrder(BUYER_ID, order.orderId()).status()).isEqualTo("CANCELLED");
        assertThat(orderRows.versionOf(order.orderId())).as("다시 온 쪽은 주문을 바꾸지 않음").isEqualTo(version);
        assertThat(events.stream(OrderCancelledEvent.class).toList()).hasSize(1);
        assertThat(orderRows.historyRows(order.orderId()))
            .containsExactly(PLACED, "ORDER PENDING_PAYMENT>CANCELLED PAYMENT_FAILED payment.failed");
    }

    @Test
    @DisplayName("결제완료된 뒤 같은 재고 차감됨이 다시 와도 결제 요청을 추가로 발행하지 않는다")
    void noRequestAgainAfterPaid() {
        Wallet wallet = Wallet.create(BUYER_ID);
        wallet.deposit(50_000L);
        walletRepository.save(wallet);
        OrderDetail order = place();
        StockDeductedEvent stockDeducted = stockDeducted(order.orderId());
        eventDelivery.deliver(stockDeducted);
        assertThat(orderService.getOrder(BUYER_ID, order.orderId()).status()).isEqualTo("PAID");
        long version = orderRows.versionOf(order.orderId());

        eventDelivery.deliver(stockDeducted);

        assertThat(events.stream(OrderPaymentRequestedEvent.class).toList()).hasSize(1);
        assertThat(orderService.getOrder(BUYER_ID, order.orderId()).status()).isEqualTo("PAID");
        assertThat(orderRows.versionOf(order.orderId())).as("다시 온 쪽은 주문을 바꾸지 않음").isEqualTo(version);
        assertThat(events.stream(OrderCancelledEvent.class).toList()).isEmpty();
        assertThat(orderRows.historyRows(order.orderId()))
            .containsExactly(PLACED, "ORDER PENDING_PAYMENT>PAID payment.completed",
                "DELIVERY null>READY payment.completed");
    }

    @Test
    @DisplayName("결제 요청이 커밋된 뒤 결제 응답 전에 같은 재고 차감됨이 다시 와도 결제 요청을 다시 내지 않고 주문은 그대로(#529)")
    void noRequestAgainWhilePaymentPending() {
        OrderDetail order = place();
        // 첫 전달이 커밋된 상태 = 결제 요청 시각만 있고 결제 응답 전. 결제 도메인이 같은 흐름에서 바로 응답하므로 JDBC로 만든다
        LocalDateTime requestedAt = LocalDateTime.of(2026, 10, 14, 9, 0);
        jdbcTemplate.update("update orders.orders set payment_requested_at = ? where id = ?",
            requestedAt, order.orderId());
        long version = orderRows.versionOf(order.orderId());

        eventDelivery.deliver(stockDeducted(order.orderId()));

        assertThat(events.stream(OrderPaymentRequestedEvent.class).toList()).isEmpty();
        assertThat(orderService.getOrder(BUYER_ID, order.orderId()).status()).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbcTemplate.queryForObject("select payment_requested_at from orders.orders where id = ?",
            LocalDateTime.class, order.orderId())).isEqualTo(requestedAt);
        assertThat(orderRows.versionOf(order.orderId())).isEqualTo(version);
        assertThat(orderRows.historyRows(order.orderId())).containsExactly(PLACED);
    }

    /**
     * 25,000원 상품 2개(자녀 1) = 50,000원.
     */
    private OrderDetail place() {
        return orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, 1L, 2, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
    }

    private <T> T single(Class<T> type) {
        List<T> recorded = events.stream(type).toList();
        assertThat(recorded).hasSize(1);
        return recorded.get(0);
    }

    private static List<String> fieldNames(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static StockDeductedEvent stockDeducted(Long orderId) {
        return new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(), new StockDeductedEvent.Data(orderId));
    }

}
