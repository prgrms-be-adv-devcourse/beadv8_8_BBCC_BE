package com.bbcc.kidly.boundedcontext.order.app.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.SchedulerIdle;
import com.bbcc.kidly.boundedcontext.order.app.delivery.dto.SellerOrderView;
import com.bbcc.kidly.boundedcontext.order.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.domain.delivery.Delivery;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
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
import org.springframework.data.domain.Page;
import org.springframework.test.context.TestPropertySource;

/**
 * 판매 주문 목록이 배송 건 수와 상관없이 같은 수의 쿼리로 끝나는지(건마다 주문 · 주문상품을 읽지 않는지) 본다.
 * Hibernate 통계는 이 클래스에서만 켠다.
 */
@IntegrationTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class DeliveryServiceQueryCountIntegrationTest {

    private static final Long BUYER_ID = 1L;
    private static final Long SINGLE_SELLER_ID = 10L;
    private static final Long MANY_SELLER_ID = 20L;
    private static final Long OTHER_SELLER_ID = 30L;
    private static final int MANY = 5;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

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
    @DisplayName("배송 건이 1개든 5개든 쿼리 수가 같고, 각 항목은 자기 주문의 자기 판매자 주문상품만 순서대로 갖는다")
    void sameQueryCountRegardlessOfDeliveryCount() {
        Long singleOrderId = placeWithDelivery(SINGLE_SELLER_ID, 0);
        List<Long> manyOrderIds = new ArrayList<>();
        for (int i = 0; i < MANY; i++) {
            manyOrderIds.add(placeWithDelivery(MANY_SELLER_ID, i + 1));
        }

        long singleCount = countStatements(SINGLE_SELLER_ID);
        long manyCount = countStatements(MANY_SELLER_ID);

        assertThat(manyCount).isEqualTo(singleCount);
        assertThat(singleCount).as("배송 페이지 1번 + 그 페이지 주문(주문상품 포함) 1번").isEqualTo(2);

        Page<SellerOrderView> single = deliveryService.getSellerOrders(SINGLE_SELLER_ID, 0, 20);
        assertThat(single.getContent()).extracting(SellerOrderView::orderId).containsExactly(singleOrderId);
        assertOwnItems(single.getContent().get(0), SINGLE_SELLER_ID, singleOrderId);

        Page<SellerOrderView> many = deliveryService.getSellerOrders(MANY_SELLER_ID, 0, 20);
        assertThat(many.getContent()).extracting(SellerOrderView::orderId)
            .containsExactlyElementsOf(manyOrderIds.reversed());
        assertThat(many.getContent()).extracting(SellerOrderView::deliveryId)
            .isSortedAccordingTo((a, b) -> Long.compare(b, a));
        for (Long orderId : manyOrderIds) {
            SellerOrderView view = many.getContent().stream()
                .filter(row -> row.orderId().equals(orderId))
                .findFirst()
                .orElseThrow();
            assertOwnItems(view, MANY_SELLER_ID, orderId);
        }
    }

    private long countStatements(Long sellerId) {
        // 측정 구간에 스케줄러 쿼리가 섞이지 않게(통계는 모든 스레드를 셈, #416)
        SchedulerIdle.await(taskScheduler);
        statistics.clear();
        deliveryService.getSellerOrders(sellerId, 0, 20);
        return statistics.getPrepareStatementCount();
    }

    /**
     * 그 주문에서 그 판매자 몫 주문상품 2개만, 주문상품 ID 오름차순으로. 기대값은 트랜잭션 밖이라 주문상품을 함께 읽어 온다.
     */
    private void assertOwnItems(SellerOrderView view, Long sellerId, Long orderId) {
        Order order = orderRepository.findAllWithItemsByIdIn(List.of(orderId)).getFirst();
        List<Long> expectedItemIds = order.getItems().stream()
            .filter(item -> item.getProduct().sellerId().equals(sellerId))
            .map(OrderItem::getId)
            .toList();
        assertThat(expectedItemIds).hasSize(2);
        assertThat(view.items()).extracting(SellerOrderView.Item::orderItemId)
            .containsExactlyElementsOf(expectedItemIds)
            .isSorted();
        assertThat(view.items()).allSatisfy(item -> assertThat(item.sellerId()).isEqualTo(sellerId));
    }

    /**
     * 판매자 몫 2줄 + 다른 판매자 1줄로 주문하고, 그 판매자의 배송 1건을 만든다. 옵션 ID는 seed로 주문마다 다르게.
     */
    private Long placeWithDelivery(Long sellerId, int seed) {
        Order order = orderRepository.save(Order.place(BUYER_ID, List.of(
                line(sellerId, seed * 10L + 1),
                line(OTHER_SELLER_ID, seed * 10L + 3),
                line(sellerId, seed * 10L + 2)),
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"),
            new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15)), LocalDateTime.now()));
        deliveryRepository.save(Delivery.create(order.getId(), sellerId));
        return order.getId();
    }

    private static Order.RequestedItem line(Long sellerId, Long optionId) {
        return new Order.RequestedItem(
            new OrderItem.ProductSnapshot(optionId, optionId, sellerId, "상품", null, "110", 10_000L, 1L),
            true, null, 1, 10_000L);
    }
}
