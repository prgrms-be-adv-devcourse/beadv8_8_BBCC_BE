package com.bbcc.kidly.boundedcontext.order;

import static com.bbcc.kidly.boundedcontext.cart.in.web.CartController.CART_ITEMS_PATH;
import static com.bbcc.kidly.boundedcontext.cart.in.web.CartController.CART_PATH;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.in.web.OrderController.ORDERS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * 상품 썸네일(#1328): product.changed의 thumbnailUrl을 상품 복사본에 저장 · 갱신하고, 장바구니 줄은 지금 복사본 값, 주문상품은 주문 시점
 * 스냅샷(이후 상품 사진이 바뀌어도 그대로), 구매자 주문 목록은 대표 주문상품의 상품 ID · 썸네일을 준다. 없으면 null.
 */
@FunctionalTest
class ProductThumbnailApiTest {

    private static final Long PRODUCT_ID = 41L;
    private static final Long OPTION_ID = 410L;
    private static final Long SELLER_ID = 2L;
    private static final String FIRST = "https://cdn.kidly.test/products/41/a.jpg";
    private static final String SECOND = "https://cdn.kidly.test/products/41/b.jpg";
    private static final Map<String, String> ADDRESS = Map.of(
        "recipientName", "홍길동", "phone", "010-0000-0000", "zipCode", "12345", "address", "서울시 테스트로 1");

    @Autowired
    private RestTestClient client;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    private LocalDateTime base;

    @BeforeEach
    void setUp() {
        base = LocalDateTime.now().minusMinutes(10);
        productChanged(FIRST, base);
    }

    @Test
    @DisplayName("상품 복사본: 썸네일을 저장하고, 더 늦은 변경이면 갱신 · 더 이른 변경은 무시, 없는 값(null)이면 null로 바꾼다")
    void replicaStoresAndUpdates() {
        assertThat(replicaThumbnail()).isEqualTo(FIRST);

        productChanged(SECOND, base.plusMinutes(1));
        assertThat(replicaThumbnail()).isEqualTo(SECOND);

        productChanged("https://cdn.kidly.test/old.jpg", base.minusMinutes(1));
        assertThat(replicaThumbnail()).as("더 이른 변경은 반영하지 않는다").isEqualTo(SECOND);

        productChanged(null, base.plusMinutes(2));
        assertThat(replicaThumbnail()).isNull();
    }

    @Test
    @DisplayName("썸네일 칸 없는 JSON도 역직렬화 · 처리하고 썸네일은 null로 둔다"
        + "(JsonMapper로 읽어 수신 처리에 넘김, Kafka 경로 자체는 아님)")
    void oldMessageWithoutThumbnail() {
        String json = """
            {"eventId":"%s","occurredAt":"%s","data":{"productId":%d,"sellerId":%d,"name":"옛 메시지 상품",
             "categoryId":1,"status":"ON_SALE","createdAt":"%s","sizeFree":false,
             "options":[{"optionId":%d,"optionName":"옛 옵션","size":"110","price":25000,"soldOut":false,
                         "saleStatus":"ON_SALE"}]}}
            """.formatted(UUID.randomUUID(), base.plusMinutes(3), PRODUCT_ID, SELLER_ID, base, OPTION_ID);

        ProductChangedEvent event = jsonMapper.readValue(json, ProductChangedEvent.class);
        eventDelivery.deliver(event);

        assertThat(event.data().thumbnailUrl()).isNull();
        assertThat(jdbcTemplate.queryForObject("select product_name from orders.replica_product where option_id = ?",
            String.class, OPTION_ID)).isEqualTo("옛 메시지 상품");
        assertThat(replicaThumbnail()).isNull();
    }

    @Test
    @DisplayName("장바구니 줄은 지금 복사본 썸네일, 주문상품은 주문 시점 스냅샷이라 이후 상품 사진이 바뀌어도 주문 상세 · 목록은 그대로")
    void cartFollowsReplicaOrderKeepsSnapshot() {
        addToCart();
        cartThumbnail(FIRST);
        String orderId = placeOrder();

        productChanged(SECOND, base.plusMinutes(1));

        cartThumbnail(SECOND);
        client.get().uri(ORDERS_PATH + "/" + orderId)
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items[0].thumbnailUrl").isEqualTo(FIRST);
        client.get().uri(ORDERS_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].orderId").isEqualTo(orderId)
            .jsonPath("$.content[0].representativeProductId").isEqualTo(String.valueOf(PRODUCT_ID))
            .jsonPath("$.content[0].representativeThumbnailUrl").isEqualTo(FIRST);
        assertThat(jdbcTemplate.queryForObject("select thumbnail_url from orders.order_item where order_id = ?",
            String.class, Long.valueOf(orderId))).isEqualTo(FIRST);
    }

    @Test
    @DisplayName("칸을 더하기 전 행(썸네일 null)은 장바구니 · 주문 상세 · 목록에서 null이고, 목록의 대표 상품 ID는 그대로 나온다")
    void existingRowsAreNull() {
        addToCart();
        String orderId = placeOrder();
        jdbcTemplate.update("update orders.replica_product set thumbnail_url = null where option_id = ?", OPTION_ID);
        jdbcTemplate.update("update orders.order_item set thumbnail_url = null where order_id = ?",
            Long.valueOf(orderId));

        client.get().uri(CART_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items[0].thumbnailUrl").value(value -> assertThat(value).isNull());
        client.get().uri(ORDERS_PATH + "/" + orderId)
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items[0].thumbnailUrl").value(value -> assertThat(value).isNull());
        client.get().uri(ORDERS_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].representativeThumbnailUrl").value(value -> assertThat(value).isNull())
            .jsonPath("$.content[0].representativeProductId").isEqualTo(String.valueOf(PRODUCT_ID));
    }

    private void productChanged(String thumbnailUrl, LocalDateTime occurredAt) {
        eventDelivery.deliver(new ProductChangedEvent(UUID.randomUUID(), occurredAt, new ProductChangedEvent.Data(
            PRODUCT_ID, SELLER_ID, "썸네일 상품", 1L, ProductStatus.ON_SALE, thumbnailUrl, base, false,
            List.of(new ProductChangedEvent.Option(OPTION_ID, "네이비 / 110", "110", 25_000L, null, null, false,
                OptionSaleStatus.ON_SALE)))));
    }

    private String replicaThumbnail() {
        return jdbcTemplate.queryForObject("select thumbnail_url from orders.replica_product where option_id = ?",
            String.class, OPTION_ID);
    }

    private void addToCart() {
        client.post().uri(CART_ITEMS_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("optionId", String.valueOf(OPTION_ID), "quantity", 1))
            .exchange()
            .expectStatus().isCreated();
    }

    private void cartThumbnail(String expected) {
        client.get().uri(CART_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items[0].thumbnailUrl").isEqualTo(expected);
    }

    private String placeOrder() {
        URI location = client.post().uri(ORDERS_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("items", List.of(Map.of("optionId", String.valueOf(OPTION_ID), "quantity", 1,
                "unitPrice", 25_000)), "shippingAddress", ADDRESS))
            .exchange()
            .expectStatus().isCreated()
            .expectBody().returnResult()
            .getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        return location.getPath().substring((ORDERS_PATH + "/").length());
    }
}
