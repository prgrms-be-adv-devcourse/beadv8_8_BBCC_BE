package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProblemDetailAssertions.assertProblem;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.ownImageUrl;
import static com.bbcc.kidly.boundedcontext.product.ProductRegisterRequestFixture.productA;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductRegisterResponse;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private ClothingTopSaver clothingTopSaver;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("판매자 본인의 상품을 등록 최신순 페이지로 돌려주고, 상품 ID는 문자열이며 썸네일이 없으면 null이다")
    void listOwnProducts() {
        SavedClothingTop saved = clothingTopSaver.save();
        // 상품 A는 네이비 110(초기 재고 10) · 네이비 120(초기 재고 0) 옵션이고 판매가는 25,000원이다
        String withoutThumbnail = register(SELLER_ID, productA(saved));
        Map<String, Object> withThumbnailBody = productA(saved);
        withThumbnailBody.put("name", "상품 B");
        withThumbnailBody.put("thumbnailUrl", ownImageUrl(SELLER_ID));
        // 같은 판매자의 옵션끼리는 SKU 코드가 겹치면 안 되므로 상품 B의 SKU 코드를 비운다
        ProductRegisterRequestFixture.options(withThumbnailBody).getFirst().put("skuCode", null);
        String withThumbnail = register(SELLER_ID, withThumbnailBody);
        register(OTHER_SELLER_ID, productA(saved));

        list(TestMembers.seller(SELLER_ID))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(2)
            .jsonPath("$.content[0].productId").isEqualTo(withThumbnail)
            .jsonPath("$.content[0].name").isEqualTo("상품 B")
            .jsonPath("$.content[0].thumbnailUrl").isEqualTo(ownImageUrl(SELLER_ID))
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
        list(TestMembers.seller(SELLER_ID))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isArray()
            .jsonPath("$.content").isEmpty()
            .jsonPath("$.totalElements").isEqualTo(0)
            .jsonPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("status=STOPPED는 판매중지 상품만 돌려준다")
    void listFiltersByStatus() {
        SavedClothingTop saved = clothingTopSaver.save();
        String stopped = register(SELLER_ID, productA(saved));
        Map<String, Object> pendingBody = productWithOtherSku(saved, "상품 B", 10, 10);
        register(SELLER_ID, pendingBody);
        jdbcTemplate.update("update product.product set status = 'STOPPED' where id = ?", Long.parseLong(stopped));

        list(TestMembers.seller(SELLER_ID), "?status=STOPPED")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].productId").isEqualTo(stopped)
            .jsonPath("$.content[0].status").isEqualTo("STOPPED")
            .jsonPath("$.totalElements").isEqualTo(1);
    }

    @Test
    @DisplayName("status에 없는 값을 주면 400이고 ProblemDetail 5필드를 돌려준다")
    void rejectUnknownStatus() {
        assertProblem(list(TestMembers.seller(SELLER_ID), "?status=WRONG"), HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("SALE-09 한 줄에 품절 옵션 수와 재고 적은 옵션 수가 숫자로 오고, lowStock=true면 그런 옵션이 있는 상품만 온다")
    void listShowsSoldOutAndLowStockOptionCounts() {
        SavedClothingTop saved = clothingTopSaver.save();
        // 상품 A는 재고 10 · 0 옵션이라 품절이 하나, 상품 B는 재고 10 · 3 옵션이라 재고 적음이 하나, 상품 C는 재고 10 · 10 옵션이다
        String soldOut = register(SELLER_ID, productA(saved));
        String low = register(SELLER_ID, productWithOtherSku(saved, "상품 B", 10, 3));
        register(SELLER_ID, productWithOtherSku(saved, "상품 C", 10, 10));

        list(TestMembers.seller(SELLER_ID), "?lowStock=true")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(2)
            .jsonPath("$.content[0].productId").isEqualTo(low)
            .jsonPath("$.content[0].soldOutOptionCount").isEqualTo(0)
            .jsonPath("$.content[0].lowStockOptionCount").isEqualTo(1)
            .jsonPath("$.content[1].productId").isEqualTo(soldOut)
            .jsonPath("$.content[1].soldOutOptionCount").isEqualTo(1)
            .jsonPath("$.content[1].lowStockOptionCount").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(2);
    }

    @Test
    @DisplayName("조건에 맞는 상품이 없으면 content는 빈 배열이다")
    void listEmptyArrayWhenNothingMatches() {
        SavedClothingTop saved = clothingTopSaver.save();
        register(SELLER_ID, productA(saved));

        list(TestMembers.seller(SELLER_ID), "?status=ON_SALE&lowStock=true")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isArray()
            .jsonPath("$.content").isEmpty()
            .jsonPath("$.totalElements").isEqualTo(0);
    }

    // 같은 판매자의 옵션끼리는 SKU 코드가 겹치면 안 되므로 SKU 코드를 비우고, 옵션 두 개의 초기 재고를 정한다
    private Map<String, Object> productWithOtherSku(SavedClothingTop saved, String name, int first, int second) {
        Map<String, Object> body = productA(saved);
        body.put("name", name);
        List<Map<String, Object>> options = ProductRegisterRequestFixture.options(body);
        options.get(0).put("skuCode", null);
        options.get(0).put("initialQuantity", first);
        options.get(1).put("skuCode", null);
        options.get(1).put("initialQuantity", second);
        return body;
    }

    // 등록 API로 판매대기 상품을 만들고 상품 ID를 돌려준다
    private String register(long sellerId, Map<String, Object> body) {
        return client.post().uri(PRODUCTS_PATH)
            .headers(TestMembers.seller(sellerId))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isCreated()
            .returnResult(ProductRegisterResponse.class)
            .getResponseBody()
            .productId();
    }

    private RestTestClient.ResponseSpec list(Consumer<HttpHeaders> login) {
        return list(login, "");
    }

    private RestTestClient.ResponseSpec list(Consumer<HttpHeaders> login, String query) {
        return client.get().uri(PRODUCTS_PATH + query)
            .headers(login)
            .exchange();
    }
}
