package com.bbcc.kidly.boundedcontext.order.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderSchedulerProperties;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 스케줄러 한 실행이 실제 DB에서 여러 배치를 이어 처리하는지. 작은 건수(batch 2)로 스케줄러를 직접 만들어
 * 같은 컨텍스트의 실제 서비스로 돌린다(설정을 바꾸려고 새 컨텍스트를 띄우지 않음).
 * 앞쪽 건의 계속되는 실패는 실제 서비스(트랜잭션 프록시)에 그대로 넘기는 mock에서 그 건만 예외를 던져 만든다.
 */
@IntegrationTest
class OrderSchedulerBatchIntegrationTest {

    private static final Long BUYER_ID = 1L;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("앞쪽 주문의 만료 취소가 계속 실패해도 같은 실행에서 뒤쪽 주문들은 여러 배치에 걸쳐 취소된다")
    void frontFailureDoesNotBlockTheRest() {
        List<Long> ids = overdueOrders(5);
        OrderService failingFirst = mock(OrderService.class, delegatesTo(orderService));
        doThrow(new IllegalStateException("계속 실패")).when(failingFirst).cancelForPaymentExpiry(ids.getFirst());

        new OrderScheduler(failingFirst, new OrderSchedulerProperties(2, 10)).expireOverduePayments();

        assertThat(statusOf(ids.getFirst())).isEqualTo("PENDING_PAYMENT");
        for (Long id : ids.subList(1, 5)) {
            assertThat(statusOf(id)).isEqualTo("CANCELLED");
        }
    }

    @Test
    @DisplayName("max-per-run(3)에 닿으면 그 실행은 앞의 3건만 처리하고 멈추며, 나머지는 다음 실행에서 처리된다")
    void stopsAtMaxPerRunAndContinuesNextRun() {
        List<Long> ids = overdueOrders(5);
        OrderScheduler scheduler = new OrderScheduler(orderService, new OrderSchedulerProperties(2, 3));

        scheduler.expireOverduePayments();

        assertThat(ids).extracting(this::statusOf)
            .containsExactly("CANCELLED", "CANCELLED", "CANCELLED", "PENDING_PAYMENT", "PENDING_PAYMENT");

        scheduler.expireOverduePayments();

        assertThat(ids).extracting(this::statusOf).containsOnly("CANCELLED");
    }

    @Test
    @DisplayName("자동 구매확정도 앞쪽 주문상품이 계속 실패해도 뒤쪽 주문상품을 같은 실행에서 여러 배치에 걸쳐 확정한다")
    void autoConfirmFrontFailureDoesNotBlockTheRest() {
        List<Long> orderIds = deliveredLongAgo(4);
        OrderService failingFirst = mock(OrderService.class, delegatesTo(orderService));
        Long firstItemId = itemIdOf(orderIds.getFirst());
        doThrow(new IllegalStateException("계속 실패")).when(failingFirst)
            .confirmAutomatically(orderIds.getFirst(), firstItemId);

        new OrderScheduler(failingFirst, new OrderSchedulerProperties(2, 10)).autoConfirm();

        assertThat(itemStatusOf(orderIds.getFirst())).isEqualTo("ORDERED");
        for (Long orderId : orderIds.subList(1, 4)) {
            assertThat(itemStatusOf(orderId)).isEqualTo("CONFIRMED");
        }
    }

    /**
     * 결제 마감이 1분 전에 지난 결제대기 주문 count건. 주문 ID 오름차순.
     */
    private List<Long> overdueOrders(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Long id = orderRepository.save(place()).getId();
            jdbcTemplate.update("update orders.orders set payment_due_at = ? where id = ?",
                LocalDateTime.now().minusMinutes(1), id);
            ids.add(id);
        }
        return ids;
    }

    /**
     * 결제완료 · 자동 확정 기간보다 하루 더 전에 배송 완료된 주문상품 하나씩인 주문 count건.
     */
    private List<Long> deliveredLongAgo(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Long id = orderRepository.save(place()).getId();
            jdbcTemplate.update("update orders.orders set status = 'PAID', paid_at = ?, payment_id = ? where id = ?",
                LocalDateTime.now().minusDays(30), 500L + i, id);
            jdbcTemplate.update("update orders.order_item set delivered_at = ? where order_id = ?",
                LocalDateTime.now().minusDays(30), id);
            ids.add(id);
        }
        return ids;
    }

    private String statusOf(Long orderId) {
        return jdbcTemplate.queryForObject("select status from orders.orders where id = ?", String.class, orderId);
    }

    private Long itemIdOf(Long orderId) {
        return jdbcTemplate.queryForObject("select id from orders.order_item where order_id = ?", Long.class, orderId);
    }

    private String itemStatusOf(Long orderId) {
        return jdbcTemplate.queryForObject(
            "select status from orders.order_item where order_id = ?", String.class, orderId);
    }

    private static Order place() {
        return Order.place(BUYER_ID, List.of(new Order.RequestedItem(
                new OrderItem.ProductSnapshot(1L, 1L, 2L, "상품", null, "110", 10_000L, 1L), true, null, 1, 10_000L)),
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"),
            new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15)), LocalDateTime.now());
    }
}
