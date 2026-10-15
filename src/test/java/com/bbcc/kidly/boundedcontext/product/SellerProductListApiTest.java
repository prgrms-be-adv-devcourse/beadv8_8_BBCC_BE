package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductRegisterResponse;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 판매자 내 상품 목록 API를 HTTP 요청부터 응답까지 확인한다. 상품은 등록 API로 만든다. 테스트에 @Transactional을 붙이지 않는다.
 */
@FunctionalTest
class SellerProductListApiTest {

    private static final String PRODUCTS_PATH = "/api/v1/seller/products";
    private static final long SELLER_ID = 7L;
    private static final long OTHER_SELLER_ID = 8L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private ClothingTopSaver clothingTopSaver;

    @Test
    @DisplayName("판매자 본인의 상품을 등록 최신순 페이지로 돌려주고, 상품 ID는 문자열이며 썸네일이 없으면 null이다")
    void listOwnProducts() {
        SavedClothingTop saved = clothingTopSaver.save();
        // 상품 A는 네이비 110(초기 재고 10) · 네이비 120(초기 재고 0) 옵션이고 판매가는 25,000원이다
        String withoutThumbnail = register(SELLER_ID, productA(saved));
        Map<String, Object> withThumbnailBody = productA(saved);
        withThumbnailBody.put("name", "상품 B");
        withThumbnailBody.put("thumbnailUrl", "https://cdn.kidly.test/b-thumbnail.jpg");
        // 같은 판매자의 옵션끼리는 SKU 코드가 겹치면 안 되므로 상품 B의 SKU 코드를 비운다
        ProductRegisterRequestFixture.options(withThumbnailBody).getFirst().put("skuCode", null);
        String withThumbnail = register(SELLER_ID, withThumbnailBody);
        register(OTHER_SELLER_ID, productA(saved));

        list(tokens.seller(SELLER_ID))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(2)
            .jsonPath("$.content[0].productId").isEqualTo(withThumbnail)
            .jsonPath("$.content[0].name").isEqualTo("상품 B")
            .jsonPath("$.content[0].thumbnailUrl").isEqualTo("https://cdn.kidly.test/b-thumbnail.jpg")
            .jsonPath("$.content[0].status").isEqualTo("SALE_PENDING")
            .jsonPath("$.content[0].optionCount").isEqualTo(2)
            .jsonPath("$.content[0].totalAvailableQuantity").isEqualTo(10)
            .jsonPath("$.content[0].minPrice").isEqualTo(25000)
            .jsonPath("$.content[0].createdAt").isNotEmpty()
            .jsonPath("$.content[1].productId").isEqualTo(withoutThumbnail)
            .jsonPath("$.content[1].thumbnailUrl").isEqualTo(null)
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(10)
            .jsonPath("$.totalElements").isEqualTo(2)
            .jsonPath("$.totalPages").isEqualTo(1)
            .jsonPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("상품이 없는 판매자는 빈 목록을 받는다")
    void listEmptyWhenNoProducts() {
        list(tokens.seller(SELLER_ID))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isArray()
            .jsonPath("$.content").isEmpty()
            .jsonPath("$.totalElements").isEqualTo(0)
            .jsonPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("토큰이 없으면 401, 판매자가 아닌 회원이면 403이다")
    void rejectWithoutSellerRole() {
        list(null).expectStatus().isUnauthorized();
        list(tokens.buyer(SELLER_ID)).expectStatus().isForbidden();
    }

    // 등록 API로 판매대기 상품을 만들고 상품 ID를 돌려준다
    private String register(long sellerId, Map<String, Object> body) {
        return client.post().uri(PRODUCTS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.seller(sellerId)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isCreated()
            .returnResult(ProductRegisterResponse.class)
            .getResponseBody()
            .productId();
    }

    private RestTestClient.ResponseSpec list(String token) {
        return client.get().uri(PRODUCTS_PATH)
            .headers(headers -> {
                if (token != null) {
                    headers.setBearerAuth(token);
                }
            })
            .exchange();
    }
}
