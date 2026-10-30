package com.bbcc.kidly.boundedcontext.order.app.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.SchedulerIdle;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderStatusHistoryRepository;
import com.bbcc.kidly.boundedcontext.order.domain.history.OrderStatusHistory;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.TestPropertySource;

/**
 * 관리자 주문 상태 이력 조회(#442)가 이력 수와 상관없이 같은 수의 쿼리로 끝나는지 본다.
 * Hibernate 통계는 다른 쿼리 수 테스트와 같은 설정으로 켠다(같은 컨텍스트를 재사용).
 */
@IntegrationTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class OrderHistoryQueryCountIntegrationTest {

    private static final Long BUYER_ID = 102L;
    private static final int MANY = 10;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderStatusHistoryRepository historyRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    @DisplayName("이력이 1줄이든 10줄이든 쿼리 수가 같다(주문 있는지 1번 + 이력 1번)")
    void sameQueryCountRegardlessOfHistorySize() {
        Long single = placeWithHistory(1);
        Long many = placeWithHistory(MANY);

        long singleCount = countStatements(single);
        long manyCount = countStatements(many);

        assertThat(manyCount).isEqualTo(singleCount);
        assertThat(singleCount).as("주문 있는지 1번 + 이력 1번").isEqualTo(2);
        assertThat(orderService.getHistory(many)).hasSize(MANY);
    }

    private long countStatements(Long orderId) {
        // 측정 구간에 스케줄러 쿼리가 섞이지 않게(통계는 모든 스레드를 셈, #416)
        SchedulerIdle.await(taskScheduler);
        statistics.clear();
        orderService.getHistory(orderId);
        return statistics.getPrepareStatementCount();
    }

    private Long placeWithHistory(int rows) {
        Order order = orderRepository.save(Order.place(BUYER_ID, List.of(new Order.RequestedItem(
                new OrderItem.ProductSnapshot(1L, 1L, 2L, "상품", null, null, 10_000L, 1L), true, null, 1, 10_000L)),
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"),
            new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15)), LocalDateTime.now()));
        for (int i = 0; i < rows; i++) {
            historyRepository.save(OrderStatusHistory.of(order.getId(), OrderStatusHistory.TargetType.ORDER,
                order.getId(), null, "PENDING_PAYMENT", null, OrderStatusHistory.ActorType.SYSTEM, null, "test",
                LocalDateTime.now()));
        }
        return order.getId();
    }
}
