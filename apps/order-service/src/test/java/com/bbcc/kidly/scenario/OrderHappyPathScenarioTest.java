package com.bbcc.kidly.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.in.event.ChildProfileEventListener;
import com.bbcc.kidly.boundedcontext.order.in.event.SellerReplicaEventListener;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주문 정상 흐름 ①~⑥(#359). kidly-app에서 주문을 빼며(#826) 주문 서비스로 옮겼다. 사람 행동은 주문 서비스의 실제 API로 하고,
 * 다른 서비스(회원 · 판매자 · 자녀는 회원 서비스, 상품 · 재고는 상품 서비스, 결제는 결제 서비스)가 보내는 seller.changed ·
 * child.changed · product.changed · stock.deducted · payment.completed는 테스트가 직접 넣는다. 판매자 · 자녀 이벤트는 Kafka 리스너가
 * 부르는 처리 메서드로, 주문이 Spring으로도 받는 상품 · 재고 · 결제 결과는 발행으로 넣는다(테스트는 Kafka 수신을 끈다).
 * 확인은 단계별 결과(상태 · 결제 요청)만 본다. 정산 · 리뷰 쪽 반응은 각 서비스 테스트가 본다.
 */
@FunctionalTest
class OrderHappyPathScenarioTest {

    private static final Long BUYER_ID = 1L;
    private static final Long SELLER_ID = 2L;
    private static final long PRICE = 25_000L;
    private static final long PRODUCT_ID = 1L;
    private static final long OPTION_ID = 1L;
    private static final long CATEGORY_ID = 1L;
    private static final long PAYMENT_ID = 1L;
    private static final long CHILD_ID = 1L;
    private static final String BUSINESS_NAME = "키들리샵";

    @Autowired
    private RestTestClient client;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private SellerReplicaEventListener sellerReplicaEventListener;

    @Autowired
    private ChildProfileEventListener childProfileEventListener;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long buyerId;
    private Long sellerId;

    @Test
    @DisplayName("가입 → 판매자 등록 → 상품 판매 시작 → 자녀 등록 → 담기 · 주문(재고 · 결제) → 배송 → 구매확정이 끝까지 이어진다")
    void happyPath() {
        buyerId = signUp(BUYER_ID);
        sellerId = signUp(SELLER_ID);

        // ① 판매자 등록: 회원 서비스가 승인 때 보내는 seller.changed → 주문 판매자 복사본
        SellerChangedEvent sellerChanged = new SellerChangedEvent(
            UUID.randomUUID(), LocalDateTime.now(),
            new SellerChangedEvent.Data(sellerId, BUSINESS_NAME, SellerStatus.ACTIVE));
        sellerReplicaEventListener.onSellerChanged(sellerChanged);
        assertThat(queryString("select business_name from orders.replica_seller where seller_id = ?", sellerId))
            .as("① 판매자 승인 뒤 주문 판매자 복사본 상호명").isEqualTo(BUSINESS_NAME);

        // ② 상품 판매 시작: 상품 서비스가 보내는 product.changed → 주문 상품 복사본 구매 가능
        String optionId = String.valueOf(OPTION_ID);
        publish(new ProductChangedEvent(UUID.randomUUID(), LocalDateTime.now(), new ProductChangedEvent.Data(
            PRODUCT_ID, sellerId, "줄무늬 양말", CATEGORY_ID, ProductStatus.ON_SALE, null, LocalDateTime.now(), false,
            List.of(new ProductChangedEvent.Option(OPTION_ID, "색상: 네이비", null, PRICE, null, null, false,
                OptionSaleStatus.ON_SALE)))));
        assertThat(queryString("select product_status from orders.replica_product where option_id = ?",
            OPTION_ID)).as("② 판매 시작 뒤 주문 상품 복사본 판매 상태").isEqualTo("ON_SALE");

        // ③ 자녀 이벤트 처리: child.changed → 주문 자녀 복사본
        Long childId = CHILD_ID;
        childProfileEventListener.onChildChanged(new ChildChangedEvent(
            UUID.randomUUID(), LocalDateTime.now(), new ChildChangedEvent.Data(childId, buyerId,
                new BigDecimal("120.5"), new BigDecimal("22.0"), Gender.MALE, LocalDate.now().minusYears(7),
                null, null, false)));
        assertThat(queryLong("select member_id from orders.replica_child where child_id = ?", childId))
            .as("③ 자녀 등록 뒤 주문 자녀 복사본").isEqualTo(buyerId);

        // ④ 담기 · 주문: order.created → (상품 서비스) 재고 차감 · stock.deducted → 결제 요청 → (결제 서비스) payment.completed → PAID
        client.post().uri("/api/v1/cart/items")
            .headers(TestMembers.buyer(buyerId))
            .body(Map.of("optionId", optionId, "childId", String.valueOf(childId), "quantity", 1))
            .exchange()
            .expectStatus().isCreated();
        URI location = client.post().uri("/api/v1/orders")
            .headers(TestMembers.buyer(buyerId))
            .body(Map.of(
                "items", List.of(Map.of("optionId", optionId, "childId", String.valueOf(childId), "quantity", 1,
                    "unitPrice", PRICE)),
                "shippingAddress", Map.of("recipientName", "홍길동", "phone", "010-0000-0000", "zipCode", "12345",
                    "address", "서울시 테스트로 1")))
            .exchange()
            .expectStatus().isCreated()
            .expectBody().returnResult().getResponseHeaders().getLocation();
        assertThat(location).as("④ 주문 Location").isNotNull();
        String orderId = location.getPath().substring("/api/v1/orders/".length());

        OrderBody pending = order(orderId);
        assertThat(pending.status()).as("④ 재고 차감 전 주문 상태").isEqualTo("PENDING_PAYMENT");
        assertThat(orderOutboxCount(orderId, "order.created")).as("④ 주문하면 상품 서비스로 주문 생성됨을 보낸다").isEqualTo(1L);
        assertThat(orderOutboxCount(orderId, "order.payment-requested")).as("④ 재고 차감 전에는 결제를 요청하지 않는다")
            .isZero();
        publish(new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductedEvent.Data(Long.valueOf(orderId))));
        assertThat(orderOutboxCount(orderId, "order.payment-requested")).as("④ 재고 차감됨을 받으면 결제 요청")
            .isEqualTo(1L);
        Long paidItemId = Long.valueOf(pending.items().getFirst().orderItemId());
        publish(new PaymentCompletedEvent(
            UUID.randomUUID(), LocalDateTime.now(), new PaymentCompletedEvent.Data(PAYMENT_ID, Long.valueOf(orderId),
                buyerId, PRICE, List.of(new PaymentCompletedEvent.Item(paidItemId, PRICE)), LocalDateTime.now())));
        OrderBody paid = order(orderId);
        assertThat(paid.status()).as("④ 주문 상태").isEqualTo("PAID");
        assertThat(paid.deliveries()).as("④ 판매자 배송 건").extracting(DeliveryRow::status).containsExactly("READY");
        assertThat(cartLineCount()).as("④ 주문한 장바구니 줄 정리").isEqualTo(0L);

        // ⑤ 배송: 발송 → 배송 완료 → 주문상품 배송 완료 시각
        String sellerOrderId = paid.deliveries().getFirst().sellerOrderId();
        client.post().uri("/api/v1/seller/orders/" + sellerOrderId + "/ship")
            .headers(TestMembers.seller(sellerId))
            .body(Map.of("carrier", "CJ", "trackingNumber", "1234567890"))
            .exchange()
            .expectStatus().isOk();
        client.post().uri("/api/v1/seller/orders/" + sellerOrderId + "/deliver")
            .headers(TestMembers.seller(sellerId))
            .exchange()
            .expectStatus().isOk();
        OrderBody delivered = order(orderId);
        assertThat(delivered.deliveries()).as("⑤ 배송 상태").extracting(DeliveryRow::status)
            .containsExactly("DELIVERED");
        assertThat(delivered.items().getFirst().deliveredAt()).as("⑤ 주문상품 배송 완료 시각").isNotNull();

        // ⑥ 구매확정: order-item.purchase-confirmed 발행. 정산 대상 · 지급은 정산 서비스(#729)가 받아 처리하므로 여기서는 보지 않는다
        String orderItemId = delivered.items().getFirst().orderItemId();
        client.post().uri("/api/v1/orders/" + orderId + "/items/" + orderItemId + "/confirm")
            .headers(TestMembers.buyer(buyerId))
            .exchange()
            .expectStatus().isOk();
    }

    /**
     * 0단계 가입: 가입은 회원 서비스가 하고 지갑은 결제 서비스가 만든다. 주문은 member.registered를 받지 않아 회원 ID만 정한다.
     */
    private Long signUp(Long memberId) {
        return memberId;
    }

    private void publish(DomainEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private long orderOutboxCount(String orderId, String eventType) {
        return queryLong("select count(*) from orders.order_outbox where message_key = ? and event_type = ?",
            orderId, eventType);
    }

    private long cartLineCount() {
        return queryLong("select count(*) from orders.cart_item where member_id = ?", buyerId);
    }

    private OrderBody order(String orderId) {
        OrderBody body = client.get().uri("/api/v1/orders/" + orderId)
            .headers(TestMembers.buyer(buyerId))
            .exchange()
            .expectStatus().isOk()
            .expectBody(OrderBody.class).returnResult().getResponseBody();
        assertThat(body).as("주문 상세 응답").isNotNull();
        return body;
    }

    private String queryString(String sql, Object arg) {
        return jdbcTemplate.queryForObject(sql, String.class, arg);
    }

    private Long queryLong(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    private record OrderBody(String status, List<ItemRow> items, List<DeliveryRow> deliveries) {
    }

    private record ItemRow(String orderItemId, String deliveredAt) {
    }

    private record DeliveryRow(String sellerOrderId, String status) {
    }
}
