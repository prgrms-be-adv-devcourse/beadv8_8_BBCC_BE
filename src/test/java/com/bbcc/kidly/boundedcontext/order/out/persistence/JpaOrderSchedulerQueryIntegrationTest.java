package com.bbcc.kidly.boundedcontext.order.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 두 스케줄러의 대상 조회(실제 PostgreSQL): 시각 경계(−1초 · 정확히 · +1초) · 상태 조건 · ID 오름차순 · 건수(Limit) · ID 커서.
 * 시각은 실행 시각 기준 상대값(초 단위로 잘라 DB 정밀도와 맞춤), 상태 · 시각은 준비용으로 DB 값을 직접 바꾼다.
 */
@IntegrationTest
class JpaOrderSchedulerQueryIntegrationTest {

    private static final Long BUYER_ID = 1L;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Nested
    @DisplayName("결제 마감이 지난 주문 ID")
    class OverduePayment {

        @Test
        @DisplayName("마감이 기준 시각 1초 전 · 정확히 같으면 대상, 1초 뒤면 아님. 결제대기가 아니면 마감이 지나도 아님")
        void dueAtBoundaryAndStatus() {
            LocalDateTime now = LocalDateTime.now().withNano(0);
            Long before = placeDueAt(now.minusSeconds(1));
            Long exact = placeDueAt(now);
            placeDueAt(now.plusSeconds(1));
            Long paid = placeDueAt(now.minusSeconds(1));
            jdbcTemplate.update("update orders.orders set status = 'PAID' where id = ?", paid);

            assertThat(orderRepository.findOverduePaymentOrderIds(Order.Status.PENDING_PAYMENT, now, 0L, Limit.of(10)))
                .containsExactly(before, exact);
        }

        @Test
        @DisplayName("주문 ID 오름차순으로 limit건, afterId보다 큰 ID부터")
        void orderLimitAndCursor() {
            LocalDateTime now = LocalDateTime.now().withNano(0);
            List<Long> ids = List.of(placeDueAt(now.minusMinutes(1)), placeDueAt(now.minusMinutes(1)),
                placeDueAt(now.minusMinutes(1)), placeDueAt(now.minusMinutes(1)));

            assertThat(orderRepository.findOverduePaymentOrderIds(Order.Status.PENDING_PAYMENT, now, 0L, Limit.of(2)))
                .containsExactly(ids.get(0), ids.get(1));
            assertThat(orderRepository.findOverduePaymentOrderIds(
                Order.Status.PENDING_PAYMENT, now, ids.get(1), Limit.of(2)))
                .containsExactly(ids.get(2), ids.get(3));
            assertThat(orderRepository.findOverduePaymentOrderIds(
                Order.Status.PENDING_PAYMENT, now, ids.get(3), Limit.of(2)))
                .isEqualTo(List.of());
        }
    }

    @Nested
    @DisplayName("자동 구매확정 대상")
    class AutoConfirm {

        @Test
        @DisplayName("배송 완료가 기준 1초 전 · 정확히 같으면 대상, 1초 뒤 · 배송 전(null)이면 아님. 주문됨이 아니면(확정) 아님")
        void deliveredAtBoundaryAndStatus() {
            LocalDateTime cutoff = LocalDateTime.now().withNano(0);
            Long before = deliveredItem(cutoff.minusSeconds(1));
            Long exact = deliveredItem(cutoff);
            deliveredItem(cutoff.plusSeconds(1));
            deliveredItem(null);
            Long confirmed = deliveredItem(cutoff.minusSeconds(1));
            jdbcTemplate.update("update orders.order_item set status = 'CONFIRMED' where id = ?", confirmed);

            assertThat(itemIds(orderRepository.findAutoConfirmTargets(
                OrderItem.Status.ORDERED, cutoff, 0L, Limit.of(10))))
                .containsExactly(before, exact);
        }

        @Test
        @DisplayName("(주문 ID, 주문상품 ID)를 주문상품 ID 오름차순으로 limit건, afterId보다 큰 주문상품 ID부터")
        void orderLimitAndCursor() {
            LocalDateTime cutoff = LocalDateTime.now().withNano(0);
            List<Long> items = List.of(deliveredItem(cutoff.minusDays(1)), deliveredItem(cutoff.minusDays(1)),
                deliveredItem(cutoff.minusDays(1)));

            List<OrderRepository.AutoConfirmRow> first =
                orderRepository.findAutoConfirmTargets(OrderItem.Status.ORDERED, cutoff, 0L, Limit.of(2));
            assertThat(itemIds(first)).containsExactly(items.get(0), items.get(1));
            assertThat(first.getFirst().getOrderId()).isEqualTo(orderIdOf(items.get(0)));
            assertThat(itemIds(orderRepository.findAutoConfirmTargets(
                OrderItem.Status.ORDERED, cutoff, items.get(1), Limit.of(2))))
                .containsExactly(items.get(2));
        }
    }

    private Long placeDueAt(LocalDateTime dueAt) {
        Long orderId = orderRepository.save(place()).getId();
        jdbcTemplate.update("update orders.orders set payment_due_at = ? where id = ?", dueAt, orderId);
        return orderId;
    }

    /**
     * 주문상품 하나인 주문을 만들고 그 주문상품의 배송 완료 시각을 정한다(null이면 배송 전). 주문상품 ID를 돌려준다.
     */
    private Long deliveredItem(LocalDateTime deliveredAt) {
        Long orderId = orderRepository.save(place()).getId();
        Long itemId = jdbcTemplate.queryForObject(
            "select id from orders.order_item where order_id = ?", Long.class, orderId);
        jdbcTemplate.update("update orders.order_item set delivered_at = ? where id = ?", deliveredAt, itemId);
        return itemId;
    }

    private Long orderIdOf(Long itemId) {
        return jdbcTemplate.queryForObject("select order_id from orders.order_item where id = ?", Long.class, itemId);
    }

    private static List<Long> itemIds(List<OrderRepository.AutoConfirmRow> rows) {
        return rows.stream().map(OrderRepository.AutoConfirmRow::getOrderItemId).toList();
    }

    private static Order place() {
        return Order.place(BUYER_ID, List.of(new Order.RequestedItem(
                new OrderItem.ProductSnapshot(1L, 1L, 2L, "상품", null, "110", 10_000L, 1L), true, null, 1, 10_000L)),
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"),
            new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15)), LocalDateTime.now());
    }
}
