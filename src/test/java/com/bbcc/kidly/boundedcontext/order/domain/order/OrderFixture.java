package com.bbcc.kidly.boundedcontext.order.domain.order;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 도메인 테스트 데이터. 도메인 생성자 · 팩토리는 여기서만 부른다(시그니처가 바뀌면 이 파일만 고친다).
 *
 * <p>판매자 A 상품 2개(자녀 선택) + 판매자 B 상품 1개(자녀 없음) = 53,000원.
 */
final class OrderFixture {

    /**
     * 테스트용 상품 옵션: 주문 당시 상품 값 + 지금 판매 중인지.
     */
    record Product(OrderItem.ProductSnapshot snapshot, boolean purchasable) {
    }

    static final Long BUYER_ID = 1L;
    static final Long OTHER_MEMBER_ID = 2L;
    static final Long SELLER_A_ID = 10L;
    static final Long SELLER_B_ID = 20L;
    static final Long CHILD_ID = 100L;
    static final Long PAYMENT_ID = 500L;

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 12, 0);
    static final LocalDateTime PAID_AT = NOW.plusMinutes(1);
    static final LocalDateTime DELIVERED_AT = NOW.plusDays(2);

    static final Duration PAYMENT_TIMEOUT = Duration.ofMinutes(10);
    static final Duration AUTO_CONFIRM_AFTER = Duration.ofDays(15);
    static final Order.Policy POLICY = new Order.Policy(PAYMENT_TIMEOUT, AUTO_CONFIRM_AFTER);

    static final Order.ShippingAddress ADDRESS =
        new Order.ShippingAddress("김보호", "010-1234-5678", "06236", "서울시 강남구 테헤란로 1");

    private OrderFixture() {
    }

    static Product productOfSellerA() {
        return product(1L, 1001L, SELLER_A_ID, "데일리 맨투맨", "110", 19_000L, true);
    }

    static Product productOfSellerB() {
        return product(2L, 2001L, SELLER_B_ID, "기모 바지", "120", 15_000L, true);
    }

    static Product notPurchasableProduct() {
        return product(3L, 3001L, SELLER_A_ID, "품절 원피스", "100", 25_000L, false);
    }

    static Product product(Long productId, Long optionId, Long sellerId, String name, String size,
        long unitPrice, boolean purchasable) {
        return new Product(
            new OrderItem.ProductSnapshot(productId, optionId, sellerId, name, null, size, unitPrice, 7L), purchasable);
    }

    static Order.RequestedChild child() {
        return child(BUYER_ID, false);
    }

    static Order.RequestedChild child(Long memberId, boolean deleted) {
        return new Order.RequestedChild(
            new OrderItem.ChildSnapshot(CHILD_ID, "김아이", new BigDecimal("115.0"), new BigDecimal("20.0"), "NORMAL"),
            memberId, deleted);
    }

    /**
     * 화면 단가 = 현재 단가인 정상 주문 줄.
     */
    static Order.RequestedItem line(Product product, Order.RequestedChild child, int quantity) {
        return new Order.RequestedItem(product.snapshot(), product.purchasable(), child, quantity,
            product.snapshot().unitPrice());
    }

    static Order.RequestedItem lineWithRequestedPrice(Product product, long requestedUnitPrice) {
        return new Order.RequestedItem(product.snapshot(), product.purchasable(), null, 1, requestedUnitPrice);
    }

    static Order place(List<Order.RequestedItem> lines) {
        return Order.place(BUYER_ID, lines, ADDRESS, POLICY, NOW);
    }

    static Order placedOrder() {
        return place(List.of(
            line(productOfSellerA(), child(), 2),
            line(productOfSellerB(), null, 1)));
    }

    static Order paidOrder() {
        Order order = placedOrder();
        order.requestPayment(NOW);
        pay(order, PAYMENT_ID, PAID_AT);
        return order;
    }

    /**
     * 주문과 그대로 맞는 결제 완료(주문한 회원 · 총액 · 주문상품별 금액)를 반영한다.
     * 주문상품에 ID가 없으면 먼저 넣는다(결제 완료는 주문상품 ID로 대조한다).
     */
    static TransitionResult pay(Order order, Long paymentId, LocalDateTime paidAt) {
        if (order.getItems().getFirst().getId() == null) {
            withItemIds(order);
        }
        return order.markPaid(paymentId, order.getMemberId(), order.getTotalAmount(), paidItemsOf(order), paidAt);
    }

    /**
     * 주문상품마다 그 주문상품 금액 그대로인 결제 금액.
     */
    static List<Order.PaidItem> paidItemsOf(Order order) {
        return order.getItems().stream()
            .map(item -> new Order.PaidItem(item.getId(), item.getAmount()))
            .toList();
    }

    /**
     * 판매자 A 몫만 배송 완료된 주문.
     */
    static Order deliveredToSellerA() {
        Order order = paidOrder();
        order.recordDelivered(SELLER_A_ID, DELIVERED_AT);
        return order;
    }

    /**
     * 저장 전 주문에는 ID가 없어서 넣는다(예외 메시지의 주문 ID를 확인하는 테스트용).
     */
    static Order withId(Order order, Long id) {
        try {
            java.lang.reflect.Field field = Order.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(order, id);
            return order;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 저장 전 주문상품에는 ID가 없어서, 주문한 순서대로 1, 2, … ID를 넣는다(주문상품으로 찾는 테스트용).
     */
    static Order withItemIds(Order order) {
        try {
            java.lang.reflect.Field id = OrderItem.class.getDeclaredField("id");
            id.setAccessible(true);
            long next = 1;
            for (OrderItem item : order.getItems()) {
                id.set(item, next++);
            }
            return order;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static OrderItem itemOf(Order order, Long sellerId) {
        return order.getItems().stream()
            .filter(item -> item.getProduct().sellerId().equals(sellerId))
            .findFirst()
            .orElseThrow();
    }
}
