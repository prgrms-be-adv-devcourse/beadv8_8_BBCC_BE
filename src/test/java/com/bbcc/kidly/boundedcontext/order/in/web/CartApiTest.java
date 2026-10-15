package com.bbcc.kidly.boundedcontext.order.in.web;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.order.in.web.CartController.CART_ITEMS_PATH;
import static com.bbcc.kidly.boundedcontext.order.in.web.CartController.CART_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class CartApiTest {

    private static final Long OTHER_ID = 3L;
    private static final Long STOPPED_OPTION_ID = 2L;
    private static final Long OPTION_STOPPED_ID = 4L;
    private static final Long SOLD_OUT_ID = 5L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(
            ReplicaFixture.product(STOPPED_OPTION_ID, 25_000L, "STOPPED", false, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.option(
            OPTION_STOPPED_ID, "ON_SALE", "STOPPED", false, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.option(
            SOLD_OUT_ID, "ON_SALE", "ON_SALE", true, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("새 줄을 담으면 201 + Location, 같은 줄을 다시 담으면 200으로 수량을 더한다")
    void addItem() {
        URI location = client.post().uri(CART_ITEMS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .body(Map.of("optionId", "1", "childId", "1", "quantity", 2))
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.optionId").isEqualTo("1")
            .jsonPath("$.childId").isEqualTo("1")
            .jsonPath("$.productName").isEqualTo("상품 A")
            .jsonPath("$.optionName").isEqualTo("네이비 / 110")
            .jsonPath("$.unitPrice").isEqualTo(25_000)
            .jsonPath("$.lineAmount").isEqualTo(50_000)
            .returnResult()
            .getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        String cartItemId = location.getPath().substring((CART_ITEMS_PATH + "/").length());

        client.post().uri(CART_ITEMS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .body(Map.of("optionId", "1", "childId", "1", "quantity", 1))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.cartItemId").isEqualTo(cartItemId)
            .jsonPath("$.quantity").isEqualTo(3);
    }

    @Test
    @DisplayName("장바구니를 조회한다")
    void getCart() {
        String cartItemId = addAndReadId();

        client.get().uri(CART_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items.length()").isEqualTo(1)
            .jsonPath("$.items[0].cartItemId").isEqualTo(cartItemId)
            .jsonPath("$.items[0].optionName").isEqualTo("네이비 / 110")
            .jsonPath("$.items[0].sellerId").isEqualTo(String.valueOf(SELLER_ID))
            .jsonPath("$.items[0].purchasable").isEqualTo(true)
            .jsonPath("$.totalAmount").isEqualTo(25_000);
    }

    @Test
    @DisplayName("수량을 바꾼다")
    void changeQuantity() {
        String cartItemId = addAndReadId();

        client.patch().uri(CART_ITEMS_PATH + "/" + cartItemId)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .body(Map.of("quantity", 3))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.quantity").isEqualTo(3)
            .jsonPath("$.lineAmount").isEqualTo(75_000);
    }

    @Test
    @DisplayName("줄을 빼면 204이고 장바구니에서 사라진다")
    void removeItem() {
        String cartItemId = addAndReadId();

        client.delete().uri(CART_ITEMS_PATH + "/" + cartItemId)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .exchange()
            .expectStatus().isNoContent();

        client.get().uri(CART_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items.length()").isEqualTo(0)
            .jsonPath("$.totalAmount").isEqualTo(0);
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void unauthorized() {
        client.get().uri(CART_PATH)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("다른 회원의 줄은 404 CART_ITEM_NOT_FOUND")
    void othersLineIsNotFound() {
        String cartItemId = addAndReadId();

        client.patch().uri(CART_ITEMS_PATH + "/" + cartItemId)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(OTHER_ID)))
            .body(Map.of("quantity", 3))
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.title").isEqualTo("장바구니 상품을 찾을 수 없습니다");

        client.delete().uri(CART_ITEMS_PATH + "/" + cartItemId)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(OTHER_ID)))
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("자기 상품을 담으면 403 OWN_PRODUCT")
    void ownProductIsForbidden() {
        client.post().uri(CART_ITEMS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
            .body(Map.of("optionId", "1", "quantity", 1))
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.title").isEqualTo("자기 상품은 살 수 없습니다");
    }

    @Test
    @DisplayName("판매 중이 아닌 상품을 담으면 409 PRODUCT_NOT_PURCHASABLE")
    void notPurchasableIsConflict() {
        client.post().uri(CART_ITEMS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .body(Map.of("optionId", String.valueOf(STOPPED_OPTION_ID), "quantity", 1))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("구매할 수 없는 상품입니다");
    }

    @Test
    @DisplayName("상품은 판매 중이어도 옵션이 판매 중지면 담을 때 409 PRODUCT_NOT_PURCHASABLE")
    void stoppedOptionIsConflict() {
        client.post().uri(CART_ITEMS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .body(Map.of("optionId", String.valueOf(OPTION_STOPPED_ID), "quantity", 1))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("구매할 수 없는 상품입니다");
    }

    @Test
    @DisplayName("상품 · 옵션이 판매 중이어도 품절이면 담을 때 409 PRODUCT_NOT_PURCHASABLE")
    void soldOutOptionIsConflict() {
        client.post().uri(CART_ITEMS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .body(Map.of("optionId", String.valueOf(SOLD_OUT_ID), "quantity", 1))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("구매할 수 없는 상품입니다");
    }

    @Test
    @DisplayName("옵션 ID가 숫자가 아니면 400")
    void invalidOptionId() {
        client.post().uri(CART_ITEMS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .body(Map.of("optionId", "abc", "quantity", 1))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("옵션 ID가 Long 범위를 넘으면(20자리) 400")
    void tooLargeOptionId() {
        client.post().uri(CART_ITEMS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .body(Map.of("optionId", "99999999999999999999", "quantity", 1))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("경로의 장바구니 줄 ID가 숫자가 아니면 400")
    void invalidCartItemIdInPath() {
        client.patch().uri(CART_ITEMS_PATH + "/abc")
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .body(Map.of("quantity", 3))
            .exchange()
            .expectStatus().isBadRequest();
    }

    private String addAndReadId() {
        CartIdOnly body = client.post().uri(CART_ITEMS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .body(Map.of("optionId", "1", "quantity", 1))
            .exchange()
            .expectStatus().isCreated()
            .expectBody(CartIdOnly.class)
            .returnResult()
            .getResponseBody();
        assertThat(body).isNotNull();
        return body.cartItemId();
    }

    private record CartIdOnly(String cartItemId) {
    }
}
