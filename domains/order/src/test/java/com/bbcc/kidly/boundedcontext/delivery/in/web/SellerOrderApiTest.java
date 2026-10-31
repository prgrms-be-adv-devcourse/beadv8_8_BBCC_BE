package com.bbcc.kidly.boundedcontext.delivery.in.web;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.order.in.web.OrderController.ORDERS_PATH;
import static com.bbcc.kidly.boundedcontext.delivery.in.web.SellerOrderController.SELLER_ORDERS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.PaymentEvents;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.SellerReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaSeller;
import com.bbcc.kidly.boundedcontext.order.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class SellerOrderApiTest {

    private static final Long OTHER_SELLER_ID = 3L;
    private static final Map<String, String> ADDRESS = Map.of(
        "recipientName", "홍길동", "phone", "010-0000-0000", "zipCode", "12345", "address", "서울시 테스트로 1");

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private RestTestClient client;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @Autowired
    private SellerReplicaRepository sellerReplicaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String orderId;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(new ReplicaProduct.Values(
            2L, 2L, OTHER_SELLER_ID, "상품 B", null, "120", 30_000L, 2L, "ON_SALE", null, false, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
        orderId = placePaidOrder();
    }

    @Nested
    @DisplayName("판매자 주문 목록")
    class GetSellerOrders {

        @Test
        @DisplayName("그 판매자 몫 주문상품 · 배송지와 함께 6.1 목록 형식으로 돌려준다")
        void onlySellersItems() {
            client.get().uri(SELLER_ORDERS_PATH)
                .headers(TestMembers.seller(SELLER_ID))
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
                .jsonPath("$.content[0].items[0].unavailableReason").value(reason -> assertThat(reason).isNull())
                .jsonPath("$.totalElements").isEqualTo(1);
        }

        @Test
        @DisplayName("판매자 복사본이 있어도 판매 주문 목록 items[].sellerName은 늘 null(키만 있음, 판매자를 조회하지 않음)")
        void sellerNameIsAlwaysNullInSellerOrders() {
            sellerReplicaRepository.save(ReplicaSeller.create(
                new ReplicaSeller.Values(SELLER_ID, "키들리샵", "ACTIVE", OCCURRED_AT)));

            client.get().uri(SELLER_ORDERS_PATH)
                .headers(TestMembers.seller(SELLER_ID))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].items[0].sellerId").isEqualTo(String.valueOf(SELLER_ID))
                .jsonPath("$.content[0].items[0].sellerName").value(name -> assertThat(name).isNull());
        }

        @Test
        @DisplayName("배송의 주문을 읽지 못해도(주문 행이 사라짐) 200, 그 건은 배송 값은 그대로 · 주문 값은 비운다(items [])")
        void brokenOrderStillOk() {
            String sellerOrderId = sellerOrderIdOf(SELLER_ID);
            jdbcTemplate.update("delete from orders.order_item where order_id = ?", Long.valueOf(orderId));
            jdbcTemplate.update("delete from orders.orders where id = ?", Long.valueOf(orderId));

            client.get().uri(SELLER_ORDERS_PATH)
                .headers(TestMembers.seller(SELLER_ID))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[0].sellerOrderId").isEqualTo(sellerOrderId)
                .jsonPath("$.content[0].orderId").isEqualTo(orderId)
                .jsonPath("$.content[0].status").isEqualTo("READY")
                .jsonPath("$.content[0].orderedAt").value(value -> assertThat(value).isNull())
                .jsonPath("$.content[0].shippingAddress").value(value -> assertThat(value).isNull())
                .jsonPath("$.content[0].items.length()").isEqualTo(0);
        }

        @Test
        @DisplayName("기본 페이지는 page 0 · size 20이고 배송 1건이면 hasNext false")
        void defaultPage() {
            client.get().uri(SELLER_ORDERS_PATH)
                .headers(TestMembers.seller(SELLER_ID))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(20)
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.totalPages").isEqualTo(1)
                .jsonPath("$.hasNext").isEqualTo(false);
        }

        @Test
        @DisplayName("마지막 페이지 바로 다음 page는 200 · 빈 목록")
        void pageAfterLastIsEmpty() {
            client.get().uri(SELLER_ORDERS_PATH + "?page=1&size=20")
                .headers(TestMembers.seller(SELLER_ID))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(0)
                .jsonPath("$.page").isEqualTo(1)
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.hasNext").isEqualTo(false);
        }

        @Test
        @DisplayName("page가 int 최댓값(2147483647)이어도 500이 아니라 200 · 빈 목록이고 page 값은 그대로")
        void hugePageIsEmpty() {
            // page = Integer.MAX_VALUE에서는 Spring Page.hasNext()의 +1 넘침으로 true — global PageResponse 한계, backlog 후보.
            // 그래서 hasNext는 바로 아래 큰 page(hugePageHasNoNext)에서 확인한다
            client.get().uri(SELLER_ORDERS_PATH + "?page=2147483647&size=20")
                .headers(TestMembers.seller(SELLER_ID))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(0)
                .jsonPath("$.page").isEqualTo(2147483647)
                .jsonPath("$.size").isEqualTo(20)
                .jsonPath("$.totalElements").isEqualTo(1);
        }

        @Test
        @DisplayName("offset이 정확히 int 최댓값(page 2147483647 · size 1)이면 실제 DB 조회로 200 · 빈 목록")
        void offsetExactlyMaxIsQueriedFromDatabase() {
            // hasNext는 단언하지 않는다(page = Integer.MAX_VALUE의 Spring Page.hasNext() +1 넘침, global 한계)
            client.get().uri(SELLER_ORDERS_PATH + "?page=2147483647&size=1")
                .headers(TestMembers.seller(SELLER_ID))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(0)
                .jsonPath("$.page").isEqualTo(2147483647)
                .jsonPath("$.size").isEqualTo(1)
                .jsonPath("$.totalElements").isEqualTo(1);
        }

        @Test
        @DisplayName("offset이 int를 넘는 큰 page(1073741824 · size 2)는 200 · 빈 목록 · hasNext false")
        void hugePageHasNoNext() {
            client.get().uri(SELLER_ORDERS_PATH + "?page=1073741824&size=2")
                .headers(TestMembers.seller(SELLER_ID))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(0)
                .jsonPath("$.page").isEqualTo(1073741824)
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.hasNext").isEqualTo(false);
        }

        @Test
        @DisplayName("그 판매자 배송이 2건이면 size=1 첫 페이지는 hasNext true · totalPages 2")
        void firstPageHasNext() {
            placePaidOrder();

            client.get().uri(SELLER_ORDERS_PATH + "?page=0&size=1")
                .headers(TestMembers.seller(SELLER_ID))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(1)
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.totalPages").isEqualTo(2)
                .jsonPath("$.hasNext").isEqualTo(true);
        }

        @Test
        @DisplayName("size가 100을 넘으면 400")
        void tooLargePageSize() {
            client.get().uri(SELLER_ORDERS_PATH + "?size=101")
                .headers(TestMembers.seller(SELLER_ID))
                .exchange()
                .expectStatus().isBadRequest();
        }

        @ParameterizedTest(name = "size={0}")
        @ValueSource(ints = {1, 100})
        @DisplayName("size 1과 100은 200")
        void pageSizeBoundsAreAllowed(int size) {
            client.get().uri(SELLER_ORDERS_PATH + "?size=" + size)
                .headers(TestMembers.seller(SELLER_ID))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.size").isEqualTo(size);
        }

        @Test
        @DisplayName("size가 0이면 400")
        void zeroPageSize() {
            client.get().uri(SELLER_ORDERS_PATH + "?size=0")
                .headers(TestMembers.seller(SELLER_ID))
                .exchange()
                .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("page가 음수(-1)면 400")
        void negativePage() {
            client.get().uri(SELLER_ORDERS_PATH + "?page=-1")
                .headers(TestMembers.seller(SELLER_ID))
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
                .headers(TestMembers.seller(SELLER_ID))
                .body(Map.of("carrier", "CJ", "trackingNumber", "1234567890"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sellerOrderId").isEqualTo(sellerOrderId)
                .jsonPath("$.orderId").isEqualTo(orderId)
                .jsonPath("$.status").isEqualTo("SHIPPING")
                .jsonPath("$.carrier").isEqualTo("CJ")
                .jsonPath("$.trackingNumber").isEqualTo("1234567890")
                .jsonPath("$.shippedAt").exists();

            client.post().uri(SELLER_ORDERS_PATH + "/" + sellerOrderId + "/deliver")
                .headers(TestMembers.seller(SELLER_ID))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("DELIVERED")
                .jsonPath("$.carrier").isEqualTo("CJ")
                .jsonPath("$.trackingNumber").isEqualTo("1234567890")
                .jsonPath("$.shippedAt").exists()
                .jsonPath("$.deliveredAt").exists();
            deliver(sellerOrderId);

            client.get().uri(SELLER_ORDERS_PATH)
                .headers(TestMembers.seller(SELLER_ID))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].sellerOrderId").isEqualTo(sellerOrderId)
                .jsonPath("$.content[0].status").isEqualTo("DELIVERED")
                .jsonPath("$.content[0].carrier").isEqualTo("CJ")
                .jsonPath("$.content[0].trackingNumber").isEqualTo("1234567890")
                .jsonPath("$.content[0].shippedAt").exists()
                .jsonPath("$.content[0].deliveredAt").exists();

            client.get().uri(ORDERS_PATH + "/" + orderId)
                .headers(TestMembers.buyer(BUYER_ID))
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
                .headers(TestMembers.seller(SELLER_ID))
                .body(Map.of("carrier", "CJ", "trackingNumber", "1234567890"))
                .exchange()
                .expectStatus().isOk();
            deliver(sellerOrderId);

            OrderItems detail = client.get().uri(ORDERS_PATH + "/" + orderId)
                .headers(TestMembers.buyer(BUYER_ID))
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
                .headers(TestMembers.seller(OTHER_SELLER_ID))
                .body(Map.of("carrier", "CJ", "trackingNumber", "1234567890"))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.title").isEqualTo("판매 주문을 찾을 수 없습니다")
                .jsonPath("$.detail").isEqualTo("없는 판매 주문입니다. 판매 주문 목록을 새로고침해 주세요.");
        }

        @Test
        @DisplayName("발송을 두 번 요청하면 409 ALREADY_SHIPPED, 처음 발송 정보는 그대로")
        void shipTwiceIsAlreadyShipped() {
            String sellerOrderId = sellerOrderIdOf(SELLER_ID);
            ship(sellerOrderId, "CJ", "1234567890").expectStatus().isOk();

            ship(sellerOrderId, "HANJIN", "999")
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.title").isEqualTo("이미 발송된 주문입니다")
                .jsonPath("$.detail").isEqualTo("이미 발송된 주문입니다. 판매 주문 목록을 새로고침해 주세요.");

            client.get().uri(SELLER_ORDERS_PATH)
                .headers(TestMembers.seller(SELLER_ID))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].carrier").isEqualTo("CJ")
                .jsonPath("$.content[0].trackingNumber").isEqualTo("1234567890");
        }

        @Test
        @DisplayName("발송 전에 배송 완료를 요청하면 409 NOT_SHIPPED, 배송 건은 READY 그대로")
        void deliverBeforeShipIsNotShipped() {
            String sellerOrderId = sellerOrderIdOf(SELLER_ID);

            client.post().uri(SELLER_ORDERS_PATH + "/" + sellerOrderId + "/deliver")
                .headers(TestMembers.seller(SELLER_ID))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.title").isEqualTo("발송 전입니다")
                .jsonPath("$.detail").isEqualTo("발송 처리를 먼저 해 주세요.");

            client.get().uri(SELLER_ORDERS_PATH)
                .headers(TestMembers.seller(SELLER_ID))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].status").isEqualTo("READY");
        }

        @Test
        @DisplayName("모르는 택배사면 400 INVALID_CARRIER(지원 택배사 안내), 빈 송장 번호면 400")
        void invalidShipRequest() {
            String sellerOrderId = sellerOrderIdOf(SELLER_ID);

            ship(sellerOrderId, "UNKNOWN", "1234567890")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.title").isEqualTo("지원하지 않는 택배사입니다")
                .jsonPath("$.detail").isEqualTo("지원하는 택배사는 CJ · EPOST · HANJIN · LOTTE · LOGEN입니다.");

            client.post().uri(SELLER_ORDERS_PATH + "/" + sellerOrderId + "/ship")
                .headers(TestMembers.seller(SELLER_ID))
                .body(Map.of("carrier", "CJ", "trackingNumber", ""))
                .exchange()
                .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("송장 번호 51자는 400(발송 안 됨), 50자는 200이고 값 그대로 저장된다")
        void trackingNumberLengthBoundary() {
            String sellerOrderId = sellerOrderIdOf(SELLER_ID);
            String fifty = "1".repeat(50);

            ship(sellerOrderId, "CJ", "1".repeat(51)).expectStatus().isBadRequest();
            ship(sellerOrderId, "CJ", fifty)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SHIPPING")
                .jsonPath("$.trackingNumber").isEqualTo(fifty);

            client.get().uri(SELLER_ORDERS_PATH)
                .headers(TestMembers.seller(SELLER_ID))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].trackingNumber").isEqualTo(fifty);
        }

        private RestTestClient.ResponseSpec ship(String sellerOrderId, String carrier, String trackingNumber) {
            return client.post().uri(SELLER_ORDERS_PATH + "/" + sellerOrderId + "/ship")
                .headers(TestMembers.seller(SELLER_ID))
                .body(Map.of("carrier", carrier, "trackingNumber", trackingNumber))
                .exchange();
        }

        private void deliver(String sellerOrderId) {
            client.post().uri(SELLER_ORDERS_PATH + "/" + sellerOrderId + "/deliver")
                .headers(TestMembers.seller(SELLER_ID))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("DELIVERED")
                .jsonPath("$.deliveredAt").exists();
        }
    }

    /**
     * 판매자 2 상품(자녀 1) · 판매자 3 상품으로 주문하고 재고 차감됨 · 결제 완료됨 이벤트를 직접 넣어 결제완료로 만든다.
     * 주문 모듈 테스트에는 결제 도메인이 없으므로 결제 완료됨은 주문과 같은 금액으로 테스트가 만든다(PaymentEvents).
     */
    private String placePaidOrder() {
        URI location = client.post().uri(ORDERS_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
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

        eventDelivery.deliver(new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductedEvent.Data(id)));
        // 주문 모듈 테스트에는 결제 도메인이 없으므로 결제 완료됨을 직접 넣는다(#616)
        eventDelivery.deliver(PaymentEvents.completed(orderService.getOrder(BUYER_ID, id), BUYER_ID));
        return String.valueOf(id);
    }

    private String sellerOrderIdOf(Long sellerId) {
        SellerOrderPage page = client.get().uri(SELLER_ORDERS_PATH)
            .headers(TestMembers.seller(sellerId))
            .exchange()
            .expectStatus().isOk()
            .expectBody(SellerOrderPage.class)
            .returnResult()
            .getResponseBody();
        assertThat(page).isNotNull();
        return page.content().get(0).sellerOrderId();
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
