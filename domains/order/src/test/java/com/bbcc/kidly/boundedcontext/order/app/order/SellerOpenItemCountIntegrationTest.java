package com.bbcc.kidly.boundedcontext.order.app.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.domain.order.TransitionResult;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.shared.order.dto.SellerOrderItemSummary;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 판매자 미종결 주문상품 수(#1120): 주문이 취소되지 않았고 주문상품이 주문됨(ORDERED)인 그 판매자 주문상품만 센다. 구매확정 · 취소된
 * 주문상품, 주문 전체가 취소된 주문(주문상품은 ORDERED로 남는다), 다른 판매자는 빼고, 주문이 없으면 0. 상태는 도메인 메서드로 바꾼다.
 */
@IntegrationTest
class SellerOpenItemCountIntegrationTest {

    private static final Long BUYER_ID = 8_100L;
    private static final Long SELLER_ID = 31L;
    private static final Long OTHER_SELLER_ID = 32L;
    private static final Order.Policy POLICY = new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15));

    @Autowired
    private OrderQueryService orderQueries;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("ORDERED만 센다: 결제대기 · 결제완료 · 배송 완료 후 확정 전은 세고, 구매확정 · 취소된 주문상품 · 다른 판매자 상품은 뺀다")
    void countsOnlyOrderedItemsOfSeller() {
        // 판매자 상품 4줄(ORDERED 2 · CONFIRMED 1 · CANCELLED 1) + 다른 판매자 1줄(ORDERED)
        Order first = save(SELLER_ID, SELLER_ID, OTHER_SELLER_ID);
        Order second = save(SELLER_ID, SELLER_ID);
        setItemStatus(first.getItems().get(1).getId(), "CONFIRMED");
        setItemStatus(second.getItems().get(1).getId(), "CANCELLED");
        // 배송 완료 후 구매확정 전도 ORDERED라 센다
        jdbcTemplate.update("update orders.order_item set delivered_at = ? where id = ?", LocalDateTime.now(),
            second.getItems().getFirst().getId());

        assertThat(count(SELLER_ID)).isEqualTo(2);
        assertThat(count(OTHER_SELLER_ID)).isEqualTo(1);
        assertThat(orderQueries.summarize(SELLER_ID)).isEqualTo(new SellerOrderItemSummary(SELLER_ID, 2));
    }

    @Test
    @DisplayName("주문이 없는 판매자는 0(없는 판매자도 404가 아니다)")
    void zeroWithoutOrders() {
        save(OTHER_SELLER_ID);

        assertThat(count(SELLER_ID)).isZero();
        assertThat(count(999_999L)).isZero();
    }

    @Test
    @DisplayName("판매자의 모든 주문상품이 구매확정 · 취소면 0")
    void zeroWhenAllClosed() {
        Order order = save(SELLER_ID, SELLER_ID);
        setItemStatus(order.getItems().getFirst().getId(), "CONFIRMED");
        setItemStatus(order.getItems().get(1).getId(), "CANCELLED");

        assertThat(count(SELLER_ID)).isZero();
    }

    @Test
    @DisplayName("주문 전체 취소는 주문상품을 ORDERED로 남기지만 세지 않는다: 결제 기한 만료 · 결제대기 구매자 취소 · 결제완료 구매자 취소 · "
        + "재고 부족 취소 · 결제 실패 모두 0")
    void excludesCancelledOrders() {
        Long expired = saveThen(order -> assertApplied(order.expirePayment(order.getPaymentDueAt())));
        Long buyerCancelledPending = saveThen(order -> assertApplied(order.cancelByBuyer(false)));
        Long buyerCancelledPaid = saveThen(order -> {
            pay(order);
            assertApplied(order.cancelByBuyer(false));
        });
        Long outOfStock = saveThen(order -> assertApplied(order.cancelForOutOfStock(Map.of())));
        Long paymentFailed = saveThen(order -> assertApplied(order.cancel(Order.CancelReason.PAYMENT_FAILED)));

        assertThat(jdbcTemplate.queryForList("select distinct status from orders.order_item where order_id in "
            + "(?, ?, ?, ?, ?)", String.class, expired, buyerCancelledPending, buyerCancelledPaid, outOfStock,
            paymentFailed)).as("주문상품은 ORDERED로 남는다").containsExactly("ORDERED");
        assertThat(count(SELLER_ID)).isZero();

        save(SELLER_ID);
        assertThat(count(SELLER_ID)).as("취소되지 않은 주문만 센다").isOne();
    }

    @Test
    @DisplayName("결제완료 주문의 ORDERED 주문상품은 센다(취소되지 않은 주문)")
    void countsPaidOrderItems() {
        saveThen(SellerOpenItemCountIntegrationTest::pay);

        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.orders where status = 'PAID'", Long.class))
            .isOne();
        assertThat(count(SELLER_ID)).isOne();
    }

    private long count(Long sellerId) {
        return orderQueries.summarize(sellerId).openItemCount();
    }

    // 저장한 뒤 다른 트랜잭션에서 도메인 메서드로 상태를 바꾼다(주문상품 ID가 있어야 결제 완료를 반영한다)
    private Long saveThen(Consumer<Order> change) {
        Long orderId = save(SELLER_ID).getId();
        transactions.executeWithoutResult(status -> change.accept(orderRepository.findById(orderId).orElseThrow()));
        return orderId;
    }

    private static void pay(Order order) {
        LocalDateTime now = LocalDateTime.now();
        order.requestPayment(now);
        assertApplied(order.markPaid(500L, order.getMemberId(), order.getTotalAmount(), order.getItems().stream()
            .map(item -> new Order.PaidItem(item.getId(), item.getAmount())).toList(), now));
    }

    private static void assertApplied(TransitionResult result) {
        assertThat(result).isEqualTo(TransitionResult.APPLIED);
    }

    private Order save(Long... sellerIds) {
        List<Order.RequestedItem> lines = new ArrayList<>();
        for (int i = 0; i < sellerIds.length; i++) {
            long optionId = i + 1L;
            lines.add(new Order.RequestedItem(new OrderItem.ProductSnapshot(optionId, optionId, sellerIds[i], "상품",
                null, null, 10_000L, 1L), true, null, 1, 10_000L));
        }
        return orderRepository.save(Order.place(BUYER_ID, lines,
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"), POLICY,
            LocalDateTime.now()));
    }

    private void setItemStatus(Long orderItemId, String status) {
        jdbcTemplate.update("update orders.order_item set status = ? where id = ?", status, orderItemId);
    }
}
