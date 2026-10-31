package com.bbcc.kidly.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRole;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.IssuedAccessToken;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 전 도메인 정상 흐름 ①~⑦(#359). 사람 행동은 각 도메인의 실제 API, 도메인 사이는 실제 이벤트 · 리스너로만 지난다
 * (테스트는 도메인 이벤트를 직접 발행하지 않는다). 확인은 단계별 결과(상태 · 재고 · 예치금 · 정산)만 본다.
 * 회원은 실제 로그인 API로 가입시킨다(카카오는 support/FakeOAuthClient 대역). API가 없는 준비만 테스트가 직접 넣는다:
 * 상품 기준 데이터(카테고리 · 상품 유형 · 항목 값)와 구매자 예치금.
 */
@FunctionalTest
class OrderHappyPathScenarioTest {

    private static final Long ADMIN_ID = 99L;
    private static final long PRICE = 25_000L;
    private static final int INITIAL_STOCK = 10;
    private static final long BUYER_DEPOSIT = 100_000L;
    private static final String BUSINESS_NAME = "키들리샵";

    @Autowired
    private RestTestClient client;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private EntityManager entityManager;

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
        buyerId = signUp("scenario-buyer");
        sellerId = signUp("scenario-seller");
        assertThat(sellerId).as("0 구매자 · 판매자는 다른 회원").isNotEqualTo(buyerId);
        depositToBuyer();
        Long categoryId = saveSockCategory();

        // ① 판매자 등록: 신청 → 관리자 승인 → seller.changed → 주문 판매자 복사본
        Long applicationId = client.post().uri("/api/v1/members/me/seller-application")
            .headers(TestMembers.buyer(sellerId))
            .body(Map.of("businessName", BUSINESS_NAME, "representativeName", "김판매",
                "businessRegistrationNumber", "1234567890"))
            .exchange()
            .expectStatus().isCreated()
            .expectBody(IdBody.class).returnResult().getResponseBody().id();
        client.post().uri("/api/v1/admin/seller-applications/" + applicationId + "/approve")
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk();
        assertThat(queryString("select business_name from orders.replica_seller where seller_id = ?", sellerId))
            .as("① 판매자 승인 뒤 주문 판매자 복사본 상호명").isEqualTo(BUSINESS_NAME);
        // 회원은 seller.changed를 Kafka로 받아(Kafka가 없는 기능 테스트에서는 받지 않는다) 회원 판매자 상태는
        // MemberSellerEventKafkaIntegrationTest가 본다. 이후 판매자 요청은 SELLER 토큰을 직접 만들어 보낸다

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

        // ③ 자녀 등록: child.changed → 주문 자녀 복사본
        Long childId = client.post().uri("/api/v1/children")
            .headers(TestMembers.buyer(buyerId))
            .body(Map.of("name", "서준", "birthDate", LocalDate.now().minusYears(7).toString(), "gender", "MALE",
                "heightCm", 120.5, "weightKg", 22.0))
            .exchange()
            .expectStatus().isCreated()
            .expectBody(IdBody.class).returnResult().getResponseBody().id();
        assertThat(queryLong("select member_id from orders.replica_child where child_id = ?", childId))
            .as("③ 자녀 등록 뒤 주문 자녀 복사본").isEqualTo(buyerId);

        // ④ 담기 · 주문: order.created → 재고 차감 → stock.deducted → 결제 요청 → 지갑 결제 → payment.completed → PAID
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

        OrderBody paid = order(orderId);
        assertThat(paid.status()).as("④ 주문 상태").isEqualTo("PAID");
        assertThat(queryLong("select available_qty from product.inventory where product_option_id = ?",
            Long.valueOf(optionId))).as("④ 재고(초기 10 − 1)").isEqualTo(INITIAL_STOCK - 1L);
        assertThat(balanceOf(buyerId)).as("④ 구매자 예치금").isEqualTo(BUYER_DEPOSIT - PRICE);
        assertThat(queryString("select status from payment.payment where order_id = ?", Long.valueOf(orderId)))
            .as("④ 결제 상태").isEqualTo("COMPLETED");
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

        // ⑦ 정산 · 지급: 정산 실행 → 지급 요청 → 판매자 지갑 입금 → 정산 지급 완료
        // 수동 정산은 기준일이 오늘 이하이고, 기준일 0시 전에 확정된 대상만 정산한다. 오늘 확정한 대상을 넣으려고
        // 확정 시각만 지금 − 1일로 옮기고 기준일 = 오늘로 실행한다(정산 운영 코드는 바꾸지 않음)
        jdbcTemplate.update("update settlement.settlement_item set confirmed_at = ? where order_item_id = ?",
            LocalDateTime.now().minusDays(1), Long.valueOf(orderItemId));
        client.post().uri("/api/v1/admin/settlements/run")
            .headers(TestMembers.admin(ADMIN_ID))
            .body(Map.of("cutoffOn", LocalDate.now().toString()))
            .exchange()
            .expectStatus().isCreated();
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
        assertThat(balanceOf(sellerId)).as("⑦ 판매자 예치금 = 정산 금액").isEqualTo(settlementAmount);
    }

    /**
     * 0단계 가입: 실제 로그인 API로 구매자 · 판매자를 가입시킨다(처음 로그인이면 가입). 카카오는 외부 요소라
     * 테스트 대역(support/FakeOAuthClient, 인가 코드를 그대로 소셜 사용자 ID로 씀)이 대신하고, 코드가 다르면 다른 회원이다.
     * 가입 이벤트로 결제 쪽이 지갑을 만든다. 받은 Access Token의 클레임으로 Gateway 헤더를 붙여(서비스는 토큰을 보지 않는다)
     * 내 정보를 조회하고, 그 회원 ID를 이후 요청에 쓴다.
     */
    private Long signUp(String authorizationCode) {
        TokenBody login = client.post().uri("/api/v1/auth/login/kakao")
            .body(Map.of("code", authorizationCode))
            .exchange()
            .expectStatus().isOk()
            .expectBody(TokenBody.class).returnResult().getResponseBody();
        assertThat(login).as("0 가입 로그인 응답").isNotNull();
        IdBody me = client.get().uri("/api/v1/members/me")
            .headers(IssuedAccessToken.decode(login.accessToken()).headers())
            .exchange()
            .expectStatus().isOk()
            .expectBody(IdBody.class).returnResult().getResponseBody();
        assertThat(me).as("0 가입 뒤 내 정보").isNotNull();
        return me.id();
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

    /**
     * 테스트가 만든 데이터: 충전(토스)은 외부 요소라 구매자 예치금만 저장소로 넣는다. 지갑은 가입 이벤트로 결제 쪽이 만들고,
     * 없으면 쓰는 시점에 만드는 것과 같은 방식(createIfAbsent)으로 먼저 있게 한 뒤 잔액만 더한다(중복 지갑 없음).
     */
    private void depositToBuyer() {
        walletRepository.createIfAbsent(buyerId, LocalDateTime.now());
        transactionTemplate.executeWithoutResult(status -> {
            Wallet buyer = walletRepository.findByMemberId(buyerId).orElseThrow();
            buyer.deposit(BUYER_DEPOSIT);
            walletRepository.save(buyer);
        });
    }

    private long balanceOf(Long memberId) {
        return walletRepository.findByMemberId(memberId).orElseThrow().getBalance();
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

    private record TokenBody(String accessToken) {
    }

    private record IdBody(Long id) {
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
