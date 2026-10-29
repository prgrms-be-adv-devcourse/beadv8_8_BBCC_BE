package com.bbcc.kidly.boundedcontext.order.out.persistence;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.port.CartItemRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.cart.CartItem;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주문 엔티티의 생성 · 수정 시각(created_at · updated_at). 저장하면 둘이 같은 값으로 채워지고,
 * 바꾸면 수정 시각만 늦어진다(#531: 공통 상위 클래스를 주문 domain 안의 것으로 바꿔도 그대로인지 고정).
 * 값은 DB에서 다시 읽어 비교한다(OS마다 다른 시계 정밀도에 흔들리지 않게).
 */
@IntegrationTest
class OrderEntityTimeIntegrationTest {

    private static final Long BUYER_ID = 1L;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("장바구니 줄을 저장하면 생성 · 수정 시각이 같은 값으로 채워지고, 수량을 바꾸면 수정 시각만 늦어진다")
    void cartItemTimes() {
        Long id = cartItemRepository.save(CartItem.create(BUYER_ID, 1L, null, 1)).getId();
        Times saved = timesOf("orders.cart_item", id);

        transactionTemplate.executeWithoutResult(status -> cartItemRepository.findByIdAndMemberId(id, BUYER_ID)
            .orElseThrow().changeQuantity(2));

        assertThat(saved.createdAt()).isNotNull();
        assertThat(saved.updatedAt()).isEqualTo(saved.createdAt());
        Times changed = timesOf("orders.cart_item", id);
        assertThat(changed.createdAt()).isEqualTo(saved.createdAt());
        assertThat(changed.updatedAt()).isAfter(saved.updatedAt());
    }

    @Test
    @DisplayName("주문을 저장하면 주문 · 주문상품 모두 생성 · 수정 시각이 같은 값으로 채워지고, 주문을 취소하면 주문의 수정 시각만 늦어진다")
    void orderAndItemTimes() {
        Order placed = orderRepository.save(place());
        Long orderId = placed.getId();
        Long itemId = placed.getItems().getFirst().getId();
        Times savedOrder = timesOf("orders.orders", orderId);
        Times savedItem = timesOf("orders.order_item", itemId);

        transactionTemplate.executeWithoutResult(status -> orderRepository.findById(orderId)
            .orElseThrow().cancel(Order.CancelReason.PAYMENT_FAILED));

        assertThat(savedOrder.createdAt()).isNotNull();
        assertThat(savedOrder.updatedAt()).isEqualTo(savedOrder.createdAt());
        assertThat(savedItem.createdAt()).isNotNull();
        assertThat(savedItem.updatedAt()).isEqualTo(savedItem.createdAt());
        Times cancelledOrder = timesOf("orders.orders", orderId);
        assertThat(cancelledOrder.createdAt()).isEqualTo(savedOrder.createdAt());
        assertThat(cancelledOrder.updatedAt()).isAfter(savedOrder.updatedAt());
        assertThat(timesOf("orders.order_item", itemId)).as("바뀌지 않은 주문상품은 그대로").isEqualTo(savedItem);
    }

    @Test
    @DisplayName("상품 복사본을 저장하면 생성 · 수정 시각이 같은 값으로 채워진다")
    void replicaTimes() {
        Long id = productReplicaRepository.save(
            ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT))).getId();

        Times saved = timesOf("orders.replica_product", id);

        assertThat(saved.createdAt()).isNotNull();
        assertThat(saved.updatedAt()).isEqualTo(saved.createdAt());
    }

    private record Times(LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    private Times timesOf(String table, Long id) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "select created_at, updated_at from " + table + " where id = ?", id);
        return new Times(((Timestamp) row.get("created_at")).toLocalDateTime(),
            ((Timestamp) row.get("updated_at")).toLocalDateTime());
    }

    private static Order place() {
        return Order.place(BUYER_ID, List.of(new Order.RequestedItem(
                new OrderItem.ProductSnapshot(1L, 1L, 2L, "상품", null, "110", 10_000L, 1L), true, null, 1, 10_000L)),
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"),
            new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15)), LocalDateTime.now());
    }
}
