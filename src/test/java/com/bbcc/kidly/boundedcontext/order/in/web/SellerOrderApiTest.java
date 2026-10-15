package com.bbcc.kidly.boundedcontext.order.in.web;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.order.in.web.OrderController.ORDERS_PATH;
import static com.bbcc.kidly.boundedcontext.order.in.web.SellerOrderController.SELLER_ORDERS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;

@FunctionalTest
class SellerOrderApiTest {

    private static final Long OTHER_SELLER_ID = 3L;
    private static final Map<String, String> ADDRESS = Map.of(
        "recipientName", "홍길동", "phone", "010-0000-0000", "zipCode", "12345", "address", "서울시 테스트로 1");

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @Autowired
    private WalletRepository walletRepository;

    private String orderId;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(new ReplicaProduct.Values(
            2L, 2L, OTHER_SELLER_ID, "상품 B", null, "120", 30_000L, 2L, "ON_SALE", null, false, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
        saveBuyerWallet(80_000L);
        orderId = placePaidOrder();
    }

    @Nested
    @DisplayName("판매자 주문 목록")
    class GetSellerOrders {

        @Test
        @DisplayName("그 판매자 몫 주문상품 · 배송지와 함께 6.1 목록 형식으로 돌려준다")
        void onlySellersItems() {
            client.get().uri(SELLER_ORDERS_PATH)
                .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[0].orderId").isEqualTo(orderId)
                .jsonPath("$.content[0].status").isEqualTo("READY")
                .jsonPath("$.content[0].orderedAt").exists()
                .jsonPath("$.content[0].shippingAddress.recipientName").isEqualTo("홍길동")
                .jsonPath("$.content[0].items.length()").isEqualTo(1)
                .jsonPath("$.content[0].items[0].sellerId").isEqualTo(String.valueOf(SELLER_ID))
                .jsonPath("$.content[0].items[0].optionName").isEqualTo("네이비 / 110")
                .jsonPath("$.content[0].items[0].child.childId").isEqualTo("1")
                .jsonPath("$.totalElements").isEqualTo(1);
        }

        @Test
        @DisplayName("size가 100을 넘으면 400")
        void tooLargePageSize() {
            client.get().uri(SELLER_ORDERS_PATH + "?size=101")
                .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
                .exchange()
                .expectStatus().isBadRequest();
        }

        @ParameterizedTest(name = "size={0}")
        @ValueSource(ints = {1, 100})
        @DisplayName("size 1과 100은 200")
        void pageSizeBoundsAreAllowed(int size) {
            client.get().uri(SELLER_ORDERS_PATH + "?size=" + size)
                .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.size").isEqualTo(size);
        }

        @Test
        @DisplayName("size가 0이면 400")
        void zeroPageSize() {
            client.get().uri(SELLER_ORDERS_PATH + "?size=0")
                .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
                .exchange()
                .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("page가 음수(-1)면 400")
        void negativePage() {
            client.get().uri(SELLER_ORDERS_PATH + "?page=-1")
                .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
                .exchange()
                .expectStatus().isBadRequest();
        }
    }

    @Nested
    @DisplayName("발송 · 배송 완료")
    class ShipAndDeliver {

        @Test
        @DisplayName("발송 → 배송 완료(두 번 눌러도 200) → 구매자 주문 상세에 배송 완료 · 완료 시각이 보인다")
        void shipThenDeliverTwice() {
            String sellerOrderId = sellerOrderIdOf(SELLER_ID);

            client.post().uri(SELLER_ORDERS_PATH + "/" + sellerOrderId + "/ship")
                .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
                .body(Map.of("carrier", "CJ", "trackingNumber", "1234567890"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SHIPPING")
                .jsonPath("$.carrier").isEqualTo("CJ")
                .jsonPath("$.shippedAt").exists();

            deliver(sellerOrderId);
            deliver(sellerOrderId);

            client.get().uri(ORDERS_PATH + "/" + orderId)
                .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PAID")
                .jsonPath("$.deliveries.length()").isEqualTo(2)
                .jsonPath("$.deliveries[0].sellerOrderId").isEqualTo(sellerOrderId)
                .jsonPath("$.deliveries[0].status").isEqualTo("DELIVERED")
                .jsonPath("$.deliveries[0].deliveredAt").exists()
                .jsonPath("$.deliveries[1].status").isEqualTo("READY")
                .jsonPath("$.items[?(@.deliveredAt != null)].sellerId").isEqualTo(List.of("2"));
        }

        @Test
        @DisplayName("한 판매자 몫에 배송 완료를 기록(UPDATE)한 뒤에도 주문 상세 주문상품은 주문한 순서(ID 오름차순)다")
        void itemsKeepOrderedSequenceAfterUpdate() {
            String sellerOrderId = sellerOrderIdOf(SELLER_ID);
            client.post().uri(SELLER_ORDERS_PATH + "/" + sellerOrderId + "/ship")
                .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
                .body(Map.of("carrier", "CJ", "trackingNumber", "1234567890"))
                .exchange()
                .expectStatus().isOk();
            deliver(sellerOrderId);

            OrderItems detail = client.get().uri(ORDERS_PATH + "/" + orderId)
                .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderItems.class)
                .returnResult()
                .getResponseBody();

            assertThat(detail).isNotNull();
            assertThat(detail.items()).extracting(OrderItemRow::sellerId)
                .containsExactly(String.valueOf(SELLER_ID), String.valueOf(OTHER_SELLER_ID));
            assertThat(Long.valueOf(detail.items().get(0).orderItemId()))
                .isLessThan(Long.valueOf(detail.items().get(1).orderItemId()));
        }

        @Test
        @DisplayName("남의 배송이면 404 DELIVERY_NOT_FOUND")
        void othersDeliveryIsNotFound() {
            String sellerOrderId = sellerOrderIdOf(SELLER_ID);

            client.post().uri(SELLER_ORDERS_PATH + "/" + sellerOrderId + "/ship")
                .headers(headers -> headers.setBearerAuth(tokens.seller(OTHER_SELLER_ID)))
                .body(Map.of("carrier", "CJ", "trackingNumber", "1234567890"))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.title").isEqualTo("판매 주문을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("모르는 택배사 · 빈 송장 번호면 400")
        void invalidShipRequest() {
            String sellerOrderId = sellerOrderIdOf(SELLER_ID);

            client.post().uri(SELLER_ORDERS_PATH + "/" + sellerOrderId + "/ship")
                .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
                .body(Map.of("carrier", "UNKNOWN", "trackingNumber", "1234567890"))
                .exchange()
                .expectStatus().isBadRequest();

            client.post().uri(SELLER_ORDERS_PATH + "/" + sellerOrderId + "/ship")
                .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
                .body(Map.of("carrier", "CJ", "trackingNumber", ""))
                .exchange()
                .expectStatus().isBadRequest();
        }

        private void deliver(String sellerOrderId) {
            client.post().uri(SELLER_ORDERS_PATH + "/" + sellerOrderId + "/deliver")
                .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("DELIVERED")
                .jsonPath("$.deliveredAt").exists();
        }
    }

    /**
     * 판매자 2 상품(자녀 1) · 판매자 3 상품으로 주문하고 재고 차감 이벤트만 직접 발행한다.
     * 결제 요청을 받은 결제 도메인이 준비한 지갑으로 실제 결제해 결제 완료를 보내므로 결제완료가 된다.
     */
    private String placePaidOrder() {
        URI location = client.post().uri(ORDERS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .body(Map.of(
                "items", List.of(
                    Map.of("optionId", "1", "childId", "1", "quantity", 2, "unitPrice", 25_000),
                    Map.of("optionId", "2", "quantity", 1, "unitPrice", 30_000)),
                "shippingAddress", ADDRESS))
            .exchange()
            .expectStatus().isCreated()
            .expectBody().returnResult()
            .getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        Long id = Long.valueOf(location.getPath().substring((ORDERS_PATH + "/").length()));

        publish(new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(), new StockDeductedEvent.Data(id)));
        return String.valueOf(id);
    }

    private String sellerOrderIdOf(Long sellerId) {
        SellerOrderPage page = client.get().uri(SELLER_ORDERS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.seller(sellerId)))
            .exchange()
            .expectStatus().isOk()
            .expectBody(SellerOrderPage.class)
            .returnResult()
            .getResponseBody();
        assertThat(page).isNotNull();
        return page.content().get(0).sellerOrderId();
    }

    /**
     * 결제 도메인이 order.payment-requested를 받아 실제로 결제하므로, 지갑이 없으면 payment.failed로 주문이 취소된다.
     */
    private void saveBuyerWallet(long balance) {
        Wallet wallet = Wallet.create(BUYER_ID);
        wallet.deposit(balance);
        walletRepository.save(wallet);
    }

    private void publish(DomainEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private record SellerOrderPage(List<SellerOrderRow> content) {
    }

    private record OrderItems(List<OrderItemRow> items) {
    }

    private record OrderItemRow(String orderItemId, String sellerId) {
    }

    private record SellerOrderRow(String sellerOrderId) {
    }
}
