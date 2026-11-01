package com.bbcc.kidly.boundedcontext.wish.in.web;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.wish.in.web.WishController.WISHES_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.cart.in.web.CartController;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.SellerReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaSeller;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestMembers;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 조르기 API(#640): 부모가 자기 자녀의 공용 장바구니에 담고 · 보고 · 뺀다. 상품 표시 · 구매 가능은 장바구니와 같은 기준이다.
 */
@FunctionalTest
class WishApiTest {

    private static final Long CHILD_ID = 1L;
    private static final Long OTHER_PARENT_ID = 3L;
    private static final Long SOLD_OUT_ID = 5L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private SellerReplicaRepository sellerReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.option(
            SOLD_OUT_ID, "ON_SALE", "ON_SALE", true, OCCURRED_AT)));
        sellerReplicaRepository.save(ReplicaSeller.create(
            new ReplicaSeller.Values(SELLER_ID, "키들리 상점", "ACTIVE", LocalDateTime.now())));
        // 자녀 1의 보호자는 BUYER_ID
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("담으면 201 + Location, 같은 옵션을 다시 담으면 200으로 수량을 더한다")
    void add() {
        URI location = client.post().uri(WISHES_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("childId", "1", "optionId", "1", "quantity", 2))
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.childId").isEqualTo("1")
            .jsonPath("$.optionId").isEqualTo("1")
            .jsonPath("$.productName").isEqualTo("상품 A")
            .jsonPath("$.unitPrice").isEqualTo(25_000)
            .jsonPath("$.purchasable").isEqualTo(true)
            .jsonPath("$.lineAmount").isEqualTo(50_000)
            .jsonPath("$.sellerName").isEqualTo("키들리 상점")
            .jsonPath("$.addedBy").isEqualTo("PARENT")
            .jsonPath("$.addedAt").isNotEmpty()
            .returnResult()
            .getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        String wishItemId = location.getPath().substring((WISHES_PATH + "/").length());

        client.post().uri(WISHES_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("childId", "1", "optionId", "1", "quantity", 1))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.wishItemId").isEqualTo(wishItemId)
            .jsonPath("$.quantity").isEqualTo(3);
    }

    @Test
    @DisplayName("자녀의 공용 장바구니를 보면 200이고, 장바구니와 같은 상품 표시 · 구매 가능 값이다")
    void view() {
        String wishItemId = addAndReadId();
        client.post().uri(CartController.CART_ITEMS_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("optionId", "1", "childId", "1", "quantity", 1))
            .exchange()
            .expectStatus().isCreated();
        soldOut();

        client.get().uri(WISHES_PATH + "?childId=" + CHILD_ID)
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.childId").isEqualTo("1")
            .jsonPath("$.items.length()").isEqualTo(1)
            .jsonPath("$.items[0].wishItemId").isEqualTo(wishItemId)
            .jsonPath("$.items[0].optionName").isEqualTo("네이비 / 110")
            .jsonPath("$.items[0].sellerId").isEqualTo(String.valueOf(SELLER_ID))
            .jsonPath("$.items[0].purchasable").isEqualTo(false)
            .jsonPath("$.totalAmount").isEqualTo(0);
        client.get().uri(CartController.CART_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectBody()
            .jsonPath("$.items[0].purchasable").isEqualTo(false);
    }

    @Test
    @DisplayName("빼면 204이고 목록에서 사라진다")
    void remove() {
        String wishItemId = addAndReadId();

        client.delete().uri(WISHES_PATH + "/" + wishItemId)
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isNoContent();

        client.get().uri(WISHES_PATH + "?childId=" + CHILD_ID)
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("다른 부모는 남의 자녀 공용 장바구니를 보거나 · 담거나 · 뺄 수 없다(404)")
    void otherParentRejected() {
        String wishItemId = addAndReadId();
        Consumer<HttpHeaders> other = TestMembers.buyer(OTHER_PARENT_ID);

        client.get().uri(WISHES_PATH + "?childId=" + CHILD_ID)
            .headers(other)
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("없는 자녀입니다. 자녀를 다시 선택해 주세요.");
        client.post().uri(WISHES_PATH)
            .headers(other)
            .body(Map.of("childId", "1", "optionId", "1", "quantity", 1))
            .exchange()
            .expectStatus().isNotFound();
        client.delete().uri(WISHES_PATH + "/" + wishItemId)
            .headers(other)
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("이미 조르기에서 빠진 상품입니다. 목록을 새로고침해 주세요.");

        client.get().uri(WISHES_PATH + "?childId=" + CHILD_ID)
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectBody()
            .jsonPath("$.items.length()").isEqualTo(1)
            .jsonPath("$.items[0].quantity").isEqualTo(1);
    }

    @Test
    @DisplayName("구매할 수 없는 상품은 담을 수 없다(409, 장바구니와 같은 판단)")
    void notPurchasable() {
        client.post().uri(WISHES_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("childId", "1", "optionId", String.valueOf(SOLD_OUT_ID), "quantity", 1))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("구매할 수 없는 상품입니다");
    }

    @Test
    @DisplayName("자녀를 주지 않으면 400")
    void childRequired() {
        client.get().uri(WISHES_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isBadRequest();
        client.post().uri(WISHES_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("optionId", "1", "quantity", 1))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void unauthorized() {
        client.get().uri(WISHES_PATH + "?childId=" + CHILD_ID)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    private String addAndReadId() {
        URI location = client.post().uri(WISHES_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("childId", "1", "optionId", "1", "quantity", 1))
            .exchange()
            .expectStatus().isCreated()
            .returnResult(Void.class)
            .getResponseHeaders().getLocation();
        return location.getPath().substring((WISHES_PATH + "/").length());
    }

    private void soldOut() {
        productReplicaRepository.findByOptionId(1L).ifPresent(product -> {
            product.applyIfNewer(ReplicaFixture.option(1L, "ON_SALE", "ON_SALE", true, OCCURRED_AT.plusMinutes(1)));
            productReplicaRepository.save(product);
        });
    }
}
