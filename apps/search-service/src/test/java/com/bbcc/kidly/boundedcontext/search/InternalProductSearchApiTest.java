package com.bbcc.kidly.boundedcontext.search;

import static com.bbcc.kidly.boundedcontext.search.SearchEvents.product;
import static com.bbcc.kidly.boundedcontext.search.SearchEvents.seller;

import com.bbcc.kidly.boundedcontext.search.app.SearchIndexService;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.SearchIndexes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 서비스 사이 상품 검색(GET /internal/products/search, #1191)을 Elasticsearch까지 본다.
 */
@FunctionalTest
class InternalProductSearchApiTest {

    @Autowired
    private RestTestClient client;

    @Autowired
    private SearchIndexService indexService;

    @Autowired
    private SearchIndexes indexes;

    @Test
    @DisplayName("판매중 상품 중 검색어가 맞고 표시 가격이 범위(이상 · 이하) 안인 상품만 관련도순으로 limit개까지 준다")
    void searchesWithinPriceRange() {
        indexService.applyProductChanged(product(1L, "여아 원피스", 7L, 3L, ProductStatus.ON_SALE, 15_000, 3L, 1));
        indexService.applyProductChanged(product(2L, "꽃무늬 원피스", 7L, 3L, ProductStatus.ON_SALE, 29_000, 3L, 2));
        indexService.applyProductChanged(product(3L, "레이스 원피스", 7L, 3L, ProductStatus.ON_SALE, 45_000, 3L, 3));
        indexService.applyProductChanged(product(4L, "체크 원피스", 7L, 3L, ProductStatus.STOPPED, 20_000, 3L, 4));
        indexService.applyProductChanged(product(5L, "여아 티셔츠", 7L, 3L, ProductStatus.ON_SALE, 9_000, 3L, 5));
        indexService.applySellerChanged(seller(7L, "키들리 상점"));
        indexes.refresh();

        client.get().uri(uri -> uri.path("/internal/products/search")
                .queryParam("keyword", "원피스").queryParam("minPrice", 15_000).queryParam("maxPrice", 30_000).build())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].productId").isEqualTo(2)
            .jsonPath("$[1].productId").isEqualTo(1)
            .jsonPath("$[1].name").isEqualTo("여아 원피스")
            .jsonPath("$[1].businessName").isEqualTo("키들리 상점")
            .jsonPath("$[1].price").isEqualTo(15_000)
            .jsonPath("$[1].thumbnailUrl").isEqualTo("https://cdn.kidly.test/1.jpg")
            .jsonPath("$[1].soldOut").isEqualTo(false);

        client.get().uri(uri -> uri.path("/internal/products/search")
                .queryParam("keyword", "원피스").queryParam("limit", 1).build())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1);
    }

    @Test
    @DisplayName("검색어가 없으면 400 ProblemDetail이다")
    void requiresKeyword() {
        client.get().uri("/internal/products/search")
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
