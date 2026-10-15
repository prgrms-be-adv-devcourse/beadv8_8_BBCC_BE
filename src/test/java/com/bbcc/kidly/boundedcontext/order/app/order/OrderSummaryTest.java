package com.bbcc.kidly.boundedcontext.order.app.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderSummary;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderSummaryTest {

    private static final Order.Policy POLICY = new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15));
    private static final Order.ShippingAddress ADDRESS =
        new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1");

    @Test
    @DisplayName("주문상품이 1개면 그 상품명이 대표 상품명이고 주문상품 수는 1")
    void singleItem() {
        Order order = withItemIds(place("양말"), 5L);

        OrderSummary summary = OrderSummary.from(order);

        assertThat(summary.representativeProductName()).isEqualTo("양말");
        assertThat(summary.itemCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("주문상품이 여러 개면 주문상품 ID가 가장 작은 상품명이 대표(담긴 순서가 아니라 ID 기준), 주문상품 수는 행 수")
    void smallestItemIdIsRepresentative() {
        Order order = withItemIds(place("맨투맨", "바지", "양말"), 30L, 10L, 20L);

        OrderSummary summary = OrderSummary.from(order);

        assertThat(summary.representativeProductName()).isEqualTo("바지");
        assertThat(summary.itemCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("주문상품을 읽지 못한 주문은 대표 상품명 null · 주문상품 수 0, 나머지 값은 그대로")
    void withoutItems() {
        Order order = withItemIds(place("양말"), 5L);

        OrderSummary summary = OrderSummary.withoutItems(order);

        assertThat(summary.representativeProductName()).isNull();
        assertThat(summary.itemCount()).isEqualTo(0);
        assertThat(summary.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(summary.totalAmount()).isEqualTo(order.getTotalAmount());
    }

    /**
     * 상품명마다 주문상품 하나(수량 1, 10,000원). 옵션 ID는 1부터 차례로.
     */
    private static Order place(String... productNames) {
        List<Order.RequestedItem> items = new java.util.ArrayList<>();
        long optionId = 1;
        for (String name : productNames) {
            items.add(new Order.RequestedItem(
                new OrderItem.ProductSnapshot(optionId, optionId, 2L, name, null, null, 10_000L, 1L),
                true, null, 1, 10_000L));
            optionId++;
        }
        return Order.place(1L, items, ADDRESS, POLICY, LocalDateTime.of(2026, 10, 15, 9, 0));
    }

    /**
     * 저장 전 주문상품에는 ID가 없어서, 담긴 순서대로 주어진 ID를 넣는다.
     */
    private static Order withItemIds(Order order, Long... ids) {
        try {
            java.lang.reflect.Field field = OrderItem.class.getDeclaredField("id");
            field.setAccessible(true);
            for (int i = 0; i < ids.length; i++) {
                field.set(order.getItems().get(i), ids[i]);
            }
            return order;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
