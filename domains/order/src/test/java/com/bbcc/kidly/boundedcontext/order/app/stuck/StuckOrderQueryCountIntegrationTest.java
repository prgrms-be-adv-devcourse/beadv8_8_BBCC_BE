package com.bbcc.kidly.boundedcontext.order.app.stuck;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.support.IntegrationTest;
import com.bbcc.kidly.boundedcontext.order.support.QueryCounter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 멈춘 주문 조회(#445)가 대상 수와 상관없이 같은 수의 쿼리로 끝나는지 본다(목록은 투영이라 지연 로딩 없음).
 * 쿼리 수는 {@link QueryCounter}로 센다(공용 통합 테스트 컨텍스트 그대로, #586).
 */
@IntegrationTest
class StuckOrderQueryCountIntegrationTest {

    private static final Long BUYER_ID = 103L;
    private static final int MANY = 10;

    @Autowired
    private StuckOrderService stuckOrderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private QueryCounter queryCounter;

    @Test
    @DisplayName("목록: 대상이 1건이든 10건이든 쿼리 수가 같다(한 페이지에 다 들면 목록 1번, 넘치면 목록 1번 + 건수 1번)")
    void listQueryCountRegardlessOfSize() {
        LocalDateTime now = LocalDateTime.now();
        shippingDelayed(1);
        long single = countStatements(() -> stuckOrderService.getStuckOrders("SHIPPING_DELAYED", now, 0, 20));
        shippingDelayed(MANY - 1);
        long many = countStatements(() -> stuckOrderService.getStuckOrders("SHIPPING_DELAYED", now, 0, 20));
        long fullPage = countStatements(() -> stuckOrderService.getStuckOrders("SHIPPING_DELAYED", now, 0, 5));

        assertThat(many).isEqualTo(single).isEqualTo(1);
        assertThat(fullPage).as("목록 1번 + 건수 1번").isEqualTo(2);
        assertThat(stuckOrderService.getStuckOrders("SHIPPING_DELAYED", now, 0, 20).getContent()).hasSize(MANY);
    }

    @Test
    @DisplayName("건수 요약: 대상 수와 상관없이 종류마다 1번씩 7번(아웃박스 멈춤은 맨 앞 미전송 1번)")
    void summaryQueryCountFixed() {
        LocalDateTime now = LocalDateTime.now();
        long empty = countStatements(() -> stuckOrderService.getSummary(now));
        shippingDelayed(MANY);
        long many = countStatements(() -> stuckOrderService.getSummary(now));

        assertThat(many).isEqualTo(empty).isEqualTo(7);
    }

    private long countStatements(Supplier<?> query) {
        return queryCounter.count(() -> query.get());
    }

    /**
     * 발송 기한(기본 3일)이 한참 지난 배송 준비 건을 주문마다 하나씩 만든다.
     */
    private void shippingDelayed(int count) {
        for (int i = 0; i < count; i++) {
            Order order = orderRepository.save(Order.place(BUYER_ID, List.of(new Order.RequestedItem(
                    new OrderItem.ProductSnapshot(1L, 1L, 2L, "상품", null, null, 10_000L, 1L), true, null, 1, 10_000L)),
                new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"),
                new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15)), LocalDateTime.now()));
            jdbcTemplate.update("update orders.orders set status = 'PAID', paid_at = ?, payment_requested_at = ? "
                + "where id = ?", LocalDateTime.now(), LocalDateTime.now(), order.getId());
            Long deliveryId = deliveryRepository.save(Delivery.create(order.getId(), 2L)).getId();
            jdbcTemplate.update("update orders.delivery set created_at = ? where id = ?",
                LocalDateTime.now().minusDays(10), deliveryId);
        }
    }
}
