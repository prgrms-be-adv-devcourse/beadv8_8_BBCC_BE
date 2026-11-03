package com.bbcc.kidly.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRole;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.global.security.TestAdmins;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * 전 도메인 정상 흐름 ①~⑦(#359). 사람 행동은 각 도메인의 실제 API, 도메인 사이는 실제 이벤트 · 리스너로만 지난다
 * (예외: 회원 · 판매자는 회원 서비스로, 자녀 API는 자녀 서비스로(#682), 결제는 결제 서비스로(#525) 빠져 가입 · 판매자 승인 ·
 * 자녀 등록 · 결제 · 지급 때 나가는 member.registered · seller.changed · child.changed · payment.completed ·
 * payment.payout-completed를 테스트가 직접 발행한다). 확인은 단계별 결과(상태 · 재고 · 정산)만 본다.
 * API가 없는 준비만 테스트가 직접 넣는다: 상품 기준 데이터(카테고리 · 상품 유형 · 항목 값).
 */
@FunctionalTest
class OrderHappyPathScenarioTest {

    private static final Long ADMIN_ID = 99L;
    private static final Long BUYER_ID = 1L;
    private static final Long SELLER_ID = 2L;
    private static final long PRICE = 25_000L;
    private static final int INITIAL_STOCK = 10;
    private static final long PAYMENT_ID = 1L;
    private static final long LEDGER_ID = 1L;
    private static final long CHILD_ID = 1L;
    private static final String BUSINESS_NAME = "키들리샵";

    @Autowired
    private RestTestClient client;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long buyerId;
    private Long sellerId;
    private Long navyValueId;

    @Test
    @DisplayName("가입 → 판매자 등록 → 상품 판매 시작 → 자녀 등록 → 담기 · 주문(재고 · 결제) → 배송 → 구매확정 → 정산 · 지급이 끝까지 이어진다")
    void happyPath() {
        buyerId = signUp(BUYER_ID, "buyer@kidly.test");
        sellerId = signUp(SELLER_ID, "seller@kidly.test");
        Long categoryId = saveSockCategory();

        // ① 판매자 등록: 회원 서비스가 승인 때 보내는 seller.changed → 주문 판매자 복사본
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(new SellerChangedEvent(
            UUID.randomUUID(), LocalDateTime.now(),
            new SellerChangedEvent.Data(sellerId, BUSINESS_NAME, SellerStatus.ACTIVE))));
        assertThat(queryString("select business_name from orders.replica_seller where seller_id = ?", sellerId))
            .as("① 판매자 승인 뒤 주문 판매자 복사본 상호명").isEqualTo(BUSINESS_NAME);

        // ② 상품 등록 · 판매 시작: product.changed → 주문 상품 복사본 구매 가능
        ProductBody product = client.post().uri("/api/v1/seller/products")
            .headers(TestMembers.seller(sellerId))
            .body(Map.of("name", "줄무늬 양말", "categoryId", categoryId, "imageUrls", List.of(),
                "attributes", List.of(),
                "options", List.of(Map.of("valueIds", List.of(navyValueId), "initialQuantity", INITIAL_STOCK)),
                "prices", List.of(Map.of("valueIds", List.of(), "price", PRICE)),
                "sizeSpecs", List.of()))
            .exchange()
            .expectStatus().isCreated()
            .expectBody(ProductBody.class).returnResult().getResponseBody();
        assertThat(product).as("② 상품 등록 응답").isNotNull();
        String optionId = product.options().getFirst().optionId();
        client.put().uri("/api/v1/seller/products/" + product.productId() + "/sale-status")
            .headers(TestMembers.seller(sellerId))
            .body(Map.of("status", "ON_SALE"))
            .exchange()
            .expectStatus().isOk();
        assertThat(queryString("select product_status from orders.replica_product where option_id = ?",
            Long.valueOf(optionId))).as("② 판매 시작 뒤 주문 상품 복사본 판매 상태").isEqualTo("ON_SALE");

        // ③ 자녀 이벤트 발행: child.changed → 주문 자녀 복사본
        Long childId = CHILD_ID;
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(new ChildChangedEvent(
            UUID.randomUUID(), LocalDateTime.now(), new ChildChangedEvent.Data(childId, buyerId,
                new BigDecimal("120.5"), new BigDecimal("22.0"), Gender.MALE, LocalDate.now().minusYears(7),
                null, null, false))));
        assertThat(queryLong("select member_id from orders.replica_child where child_id = ?", childId))
            .as("③ 자녀 등록 뒤 주문 자녀 복사본").isEqualTo(buyerId);

        // ④ 담기 · 주문: order.created → 재고 차감 → stock.deducted → 결제 요청 → (결제 서비스) payment.completed → PAID
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
        assertThat(pending.status()).as("④ 결제 요청 뒤 주문 상태").isEqualTo("PENDING_PAYMENT");
        assertThat(queryLong("select available_qty from product.inventory where product_option_id = ?",
            Long.valueOf(optionId))).as("④ 재고(초기 10 − 1)").isEqualTo(INITIAL_STOCK - 1L);
        Long paidItemId = Long.valueOf(pending.items().getFirst().orderItemId());
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(new PaymentCompletedEvent(
            UUID.randomUUID(), LocalDateTime.now(), new PaymentCompletedEvent.Data(PAYMENT_ID, Long.valueOf(orderId),
                buyerId, PRICE, List.of(new PaymentCompletedEvent.Item(paidItemId, PRICE)), LocalDateTime.now()))));
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

        // ⑥ 구매확정: order-item.purchase-confirmed → 판매자 정산 대상
        String orderItemId = delivered.items().getFirst().orderItemId();
        client.post().uri("/api/v1/orders/" + orderId + "/items/" + orderItemId + "/confirm")
            .headers(TestMembers.buyer(buyerId))
            .exchange()
            .expectStatus().isOk();
        assertThat(queryLong("select sales_amount from settlement.settlement_item where order_item_id = ? "
            + "and seller_id = ?", Long.valueOf(orderItemId), sellerId)).as("⑥ 판매자 정산 대상 금액").isEqualTo(PRICE);

        // ⑦ 정산 · 지급: 정산 실행 → 지급 요청 → (결제 서비스) 판매자 지갑 입금 · payment.payout-completed → 정산 지급 완료
        // 수동 정산은 기준일이 오늘 이하이고, 기준일 0시 전에 확정된 대상만 정산한다. 오늘 확정한 대상을 넣으려고
        // 확정 시각만 지금 − 1일로 옮기고 기준일 = 오늘로 실행한다(정산 운영 코드는 바꾸지 않음)
        jdbcTemplate.update("update settlement.settlement_item set confirmed_at = ? where order_item_id = ?",
            LocalDateTime.now().minusDays(1), Long.valueOf(orderItemId));
        client.post().uri("/api/v1/admin/settlements/run")
            .headers(TestAdmins.of(ADMIN_ID))
            .body(Map.of("cutoffOn", LocalDate.now().toString()))
            .exchange()
            .expectStatus().isCreated();
        Map<String, Object> requested = jdbcTemplate.queryForMap(
            "select id, settlement_amount from settlement.settlement where seller_id = ?", sellerId);
        Long settlementId = ((Number) requested.get("id")).longValue();
        long requestedAmount = ((Number) requested.get("settlement_amount")).longValue();
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(new PaymentPayoutCompletedEvent(
            UUID.randomUUID(), LocalDateTime.now(), new PaymentPayoutCompletedEvent.Data(settlementId, sellerId,
                requestedAmount, LEDGER_ID, LocalDateTime.now()))));
        Map<String, Object> settlement = jdbcTemplate.queryForMap("select status, total_sales_amount, fee_rate, "
            + "fee_amount, settlement_amount from settlement.settlement where seller_id = ?", sellerId);
        assertThat(settlement.get("total_sales_amount")).as("⑦ 정산 매출 합계").isEqualTo(PRICE);
        assertThat(settlement.get("status")).as("⑦ 정산 지급 상태").isEqualTo("PAID");
        // 수수료율은 정산 설정값이라 행에서 읽고, 수수료 = 매출 × 수수료율(원 단위 버림), 정산 금액 = 매출 − 수수료로 맞춘다
        BigDecimal feeRate = (BigDecimal) settlement.get("fee_rate");
        long feeAmount = ((Number) settlement.get("fee_amount")).longValue();
        long settlementAmount = ((Number) settlement.get("settlement_amount")).longValue();
        assertThat(feeAmount).as("⑦ 수수료 = 매출 × 수수료율")
            .isEqualTo(BigDecimal.valueOf(PRICE).multiply(feeRate).setScale(0, RoundingMode.DOWN).longValueExact());
        assertThat(settlementAmount).as("⑦ 정산 금액 = 매출 − 수수료").isEqualTo(PRICE - feeAmount);
    }

    /**
     * 0단계 가입: 회원 서비스가 가입 때 보내는 member.registered를 발행한다. 지갑은 결제 서비스가 만든다.
     */
    private Long signUp(Long memberId, String email) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(new MemberRegisteredEvent(
            UUID.randomUUID(), LocalDateTime.now(), new MemberRegisteredEvent.Data(memberId, email))));
        return memberId;
    }

    /**
     * 테스트가 만든 데이터: 상품 기준 데이터를 만드는 API가 없어서, 상품 도메인의 공개 팩토리로 최소 기준 데이터
     * (색상 옵션 하나인 양말 유형 · 잡화 > 양말 카테고리 · 네이비 값)를 저장한다. 사이즈 · 실측 항목은 없다.
     */
    private Long saveSockCategory() {
        return transactionTemplate.execute(status -> {
            Attribute color = Attribute.create("색상", "COLOR", AttributeInputType.PALETTE);
            AttributeValue navy = AttributeValue.create(color, "네이비", "#2f3e5c");
            ProductType sock = ProductType.define("양말", List.of(
                new ProductType.LinkInfo(color, AttributeRole.OPTION, true, true, false, false, List.of())));
            Category goods = Category.createMiddle("잡화", null);
            Category socks = Category.createLeaf(new Category.LeafInfo("양말", goods, sock, List.of()));
            entityManager.persist(color);
            entityManager.persist(navy);
            entityManager.persist(sock);
            entityManager.persist(goods);
            entityManager.persist(socks);
            entityManager.flush();
            navyValueId = navy.getId();
            return socks.getId();
        });
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

    private record ProductBody(String productId, List<OptionRow> options) {
    }

    private record OptionRow(String optionId) {
    }

    private record OrderBody(String status, List<ItemRow> items, List<DeliveryRow> deliveries) {
    }

    private record ItemRow(String orderItemId, String deliveredAt) {
    }

    private record DeliveryRow(String sellerOrderId, String status) {
    }
}
