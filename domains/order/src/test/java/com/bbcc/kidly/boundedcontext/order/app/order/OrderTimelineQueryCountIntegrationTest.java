package com.bbcc.kidly.boundedcontext.order.app.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderTimeline;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.SellerReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaSeller;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.boundedcontext.support.QueryCounter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 타임라인이 판매자 · 주문상품 수와 상관없이 같은 수의 쿼리로 끝나는지 본다(#352).
 * 쿼리 수는 {@link QueryCounter}로 센다(공용 통합 테스트 컨텍스트 그대로, #586).
 */
@IntegrationTest
class OrderTimelineQueryCountIntegrationTest {

    private static final Long BUYER_ID = 101L;
    private static final int MANY = 5;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private SellerReplicaRepository sellerReplicaRepository;

    @Autowired
    private QueryCounter queryCounter;

    @Test
    @DisplayName("판매자 · 배송 건이 1개든 5개든 쿼리 수가 같다(주문(주문상품 포함) 1번 + 배송 건 1번 + 판매자 상호명 1번)")
    void sameQueryCountRegardlessOfSellerCount() {
        Long single = placeWithDeliveries(1);
        Long many = placeWithDeliveries(MANY);

        long singleCount = countStatements(single);
        long manyCount = countStatements(many);

        assertThat(manyCount).isEqualTo(singleCount);
        assertThat(singleCount).as("주문(주문상품 포함) 1번 + 배송 건 1번 + 판매자 상호명 1번").isEqualTo(3);
        OrderTimeline timeline = orderService.getTimeline(BUYER_ID, many);
        OrderTimeline.Step shipped = timeline.steps().stream()
            .filter(step -> step.step() == OrderTimeline.StepName.SHIPPED).findFirst().orElseThrow();
        assertThat(shipped.deliveries()).hasSize(MANY)
            .extracting(OrderTimeline.DeliveryDetail::sellerName)
            .containsExactly("판매자 0", "판매자 1", "판매자 2", "판매자 3", "판매자 4");
        OrderTimeline.Step confirmed = timeline.steps().stream()
            .filter(step -> step.step() == OrderTimeline.StepName.CONFIRMED).findFirst().orElseThrow();
        assertThat(confirmed.items()).hasSize(MANY * 2);
    }

    private long countStatements(Long orderId) {
        return queryCounter.count(() -> orderService.getTimeline(BUYER_ID, orderId));
    }

    /**
     * 판매자마다 주문상품 2개 · 배송 건 1건 · 판매자 복사본 1건. 판매자 ID는 주문마다 다르다.
     * 주문상품을 2개씩 두어 fetch join으로 늘어난 행이 주문 1건으로 합쳐지는지도 본다.
     */
    private Long placeWithDeliveries(int sellerCount) {
        long base = sellerCount * 100L;
        List<Order.RequestedItem> items = new ArrayList<>();
        for (int i = 0; i < sellerCount; i++) {
            long sellerId = base + i;
            items.add(new Order.RequestedItem(new OrderItem.ProductSnapshot(
                i + 1L, i * 2 + 1L, sellerId, "상품 " + i, null, null, 10_000L, 1L), true, null, 1, 10_000L));
            items.add(new Order.RequestedItem(new OrderItem.ProductSnapshot(
                i + 1L, i * 2 + 2L, sellerId, "상품 " + i, null, null, 10_000L, 1L), true, null, 1, 10_000L));
        }
        Order order = orderRepository.save(Order.place(BUYER_ID, items,
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"),
            new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15)), LocalDateTime.now()));
        for (int i = 0; i < sellerCount; i++) {
            long sellerId = base + i;
            deliveryRepository.save(Delivery.create(order.getId(), sellerId));
            sellerReplicaRepository.save(ReplicaSeller.create(
                new ReplicaSeller.Values(sellerId, "판매자 " + i, "ACTIVE", LocalDateTime.now())));
        }
        return order.getId();
    }
}
