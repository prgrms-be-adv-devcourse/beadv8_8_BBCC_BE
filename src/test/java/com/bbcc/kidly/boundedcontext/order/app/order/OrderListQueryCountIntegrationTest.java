package com.bbcc.kidly.boundedcontext.order.app.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.SchedulerIdle;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.AdminOrderSummary;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderSummary;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.SellerReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaSeller;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
 * 내 주문 목록이 주문 수와 상관없이 같은 수의 쿼리로 끝나는지(주문마다 주문상품을 읽지 않는지) 본다.
 * Hibernate 통계는 판매 주문 목록 쿼리 수 테스트와 같은 설정으로 켠다(같은 컨텍스트를 재사용).
 */
@IntegrationTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class OrderListQueryCountIntegrationTest {

    private static final Long SINGLE_BUYER_ID = 101L;
    private static final Long MANY_BUYER_ID = 102L;
    private static final int MANY = 5;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private SellerReplicaRepository sellerReplicaRepository;

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
    @DisplayName("주문 · 판매자가 1건이든 5건이든 쿼리 수가 같고, 각 주문은 첫 주문상품 상품명 · 판매자 상호명 · 주문상품 수를 갖는다")
    void sameQueryCountRegardlessOfOrderCount() {
        Long singleOrderId = place(SINGLE_BUYER_ID, 0, "단일 대표", "단일 둘째");
        List<Long> manyOrderIds = new ArrayList<>();
        for (int i = 0; i < MANY; i++) {
            manyOrderIds.add(place(MANY_BUYER_ID, i, "대표 " + i, "둘째 " + i));
        }
        for (int i = 0; i < MANY; i++) {
            saveSeller(firstSellerId(i), "대표 판매자 " + i);
            saveSeller(secondSellerId(i), "둘째 판매자 " + i);
        }

        long singleCount = countStatements(SINGLE_BUYER_ID);
        long manyCount = countStatements(MANY_BUYER_ID);

        assertThat(manyCount).isEqualTo(singleCount);
        assertThat(singleCount).as("주문 페이지 1번 + 그 페이지 주문(주문상품 포함) 1번 + 판매자 상호명 1번").isEqualTo(3);

        List<OrderSummary> single = orderService.getOrders(SINGLE_BUYER_ID, 0, 20).getContent();
        assertThat(single).extracting(OrderSummary::orderId, OrderSummary::representativeProductName,
                OrderSummary::itemCount, OrderSummary::sellerName)
            .containsExactly(org.assertj.core.groups.Tuple.tuple(singleOrderId, "단일 대표", 2, "대표 판매자 0"));

        List<OrderSummary> many = orderService.getOrders(MANY_BUYER_ID, 0, 20).getContent();
        assertThat(many).extracting(OrderSummary::orderId).containsExactlyElementsOf(manyOrderIds.reversed());
        for (int i = 0; i < MANY; i++) {
            OrderSummary summary = many.get(MANY - 1 - i);
            assertThat(summary.representativeProductName()).isEqualTo("대표 " + i);
            assertThat(summary.sellerName()).isEqualTo("대표 판매자 " + i);
            assertThat(summary.itemCount()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("관리자 전체 주문 목록도 주문이 1건이든 5건이든 쿼리 수가 같다(주문 페이지 1번 + 주문상품 1번)")
    void adminListSameQueryCountRegardlessOfOrderCount() {
        place(SINGLE_BUYER_ID, 0, "첫 주문", "둘째");
        // 측정 구간에 스케줄러 쿼리가 섞이지 않게(통계는 모든 스레드를 셈, #416)
        SchedulerIdle.await(taskScheduler);
        statistics.clear();
        orderService.getAllOrders(0, 20);
        long singleCount = statistics.getPrepareStatementCount();

        for (int i = 1; i < MANY; i++) {
            place(MANY_BUYER_ID, i, "대표 " + i, "둘째 " + i);
        }
        // 측정 구간에 스케줄러 쿼리가 섞이지 않게(통계는 모든 스레드를 셈, #416)
        SchedulerIdle.await(taskScheduler);
        statistics.clear();
        List<AdminOrderSummary> all = orderService.getAllOrders(0, 20).getContent();
        long manyCount = statistics.getPrepareStatementCount();

        assertThat(manyCount).isEqualTo(singleCount);
        assertThat(singleCount).as("주문 페이지 1번 + 그 페이지 주문(주문상품 포함) 1번").isEqualTo(2);
        assertThat(all).hasSize(MANY);
        assertThat(all).extracting(AdminOrderSummary::itemCount).containsOnly(2);
    }

    private void saveSeller(Long sellerId, String businessName) {
        sellerReplicaRepository.save(ReplicaSeller.create(
            new ReplicaSeller.Values(sellerId, businessName, "ACTIVE", LocalDateTime.now())));
    }

    private static Long firstSellerId(int index) {
        return 200L + index;
    }

    private static Long secondSellerId(int index) {
        return 300L + index;
    }

    private long countStatements(Long memberId) {
        // 측정 구간에 스케줄러 쿼리가 섞이지 않게(통계는 모든 스레드를 셈, #416)
        SchedulerIdle.await(taskScheduler);
        statistics.clear();
        orderService.getOrders(memberId, 0, 20);
        return statistics.getPrepareStatementCount();
    }

    /**
     * 주문상품 2개(첫째 · 둘째 상품명)인 주문. 첫째가 먼저 저장되어 주문상품 ID가 더 작다. 판매자는 주문마다 다르다.
     */
    private Long place(Long memberId, int index, String firstName, String secondName) {
        Order order = orderRepository.save(Order.place(memberId, List.of(
                line(1L, firstSellerId(index), firstName),
                line(2L, secondSellerId(index), secondName)),
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"),
            new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15)), LocalDateTime.now()));
        return order.getId();
    }

    private static Order.RequestedItem line(Long optionId, Long sellerId, String productName) {
        return new Order.RequestedItem(
            new OrderItem.ProductSnapshot(optionId, optionId, sellerId, productName, null, "110", 10_000L, 1L),
            true, null, 1, 10_000L);
    }
}
