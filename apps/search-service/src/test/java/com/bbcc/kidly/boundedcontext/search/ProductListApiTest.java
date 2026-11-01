package com.bbcc.kidly.boundedcontext.search;

import static com.bbcc.kidly.boundedcontext.search.SearchEvents.product;
import static com.bbcc.kidly.boundedcontext.search.SearchEvents.seller;

import com.bbcc.kidly.boundedcontext.search.app.SearchIndexService;
import com.bbcc.kidly.shared.auth.MemberHeaders;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.SearchIndexes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 검색 서비스의 상품 목록 API(GET /api/v1/products)를 HTTP 요청부터 응답까지 본다. 응답은 상품의 목록 API와 같은 모양이다.
 */
@FunctionalTest
class ProductListApiTest {

    @Autowired
    private RestTestClient client;

    @Autowired
    private SearchIndexService indexService;

    @Autowired
    private SearchIndexes indexes;

    @Test
    @DisplayName("로그인 없이 검색어로 찾고, 감싸지 않은 페이지 응답을 돌려준다")
    void list() {
        indexService.applyProductChanged(product(1L, "여아 원피스", 7L, 3L, ProductStatus.ON_SALE, 15_000, 3L, 1));
        indexService.applySellerChanged(seller(7L, "키들리 상점"));
        indexes.refresh();

        client.get().uri(uri -> uri.path("/api/v1/products").queryParam("keyword", "원피스 여아용").build())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].productId").isEqualTo("1")
            .jsonPath("$.content[0].name").isEqualTo("여아 원피스")
            .jsonPath("$.content[0].businessName").isEqualTo("키들리 상점")
            .jsonPath("$.content[0].minPrice").isEqualTo(15_000)
            .jsonPath("$.content[0].soldOut").isEqualTo(false)
            .jsonPath("$.totalElements").isEqualTo(1)
            .jsonPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("Gateway가 붙인 회원 ID 헤더가 있어도 같은 목록을 돌려준다")
    void listWithMemberHeader() {
        indexService.applyProductChanged(product(1L, "여아 원피스", 7L, 3L, ProductStatus.ON_SALE, 15_000, 3L, 1));
        indexes.refresh();

        client.get().uri(uri -> uri.path("/api/v1/products").queryParam("keyword", "원피스").build())
            .header(MemberHeaders.MEMBER_ID, "7")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].productId").isEqualTo("1")
            .jsonPath("$.totalElements").isEqualTo(1);
    }

    @Test
    @DisplayName("결과가 없으면 빈 목록이다")
    void empty() {
        client.get().uri("/api/v1/products")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isEmpty()
            .jsonPath("$.totalElements").isEqualTo(0);
    }

    @Test
    @DisplayName("없는 정렬이면 400 ProblemDetail이다")
    void invalidSort() {
        client.get().uri("/api/v1/products?sort=POPULAR")
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(400)
            .jsonPath("$.instance").isEqualTo("/api/v1/products");
    }

    @Test
    @DisplayName("헬스 체크는 열려 있고 Elasticsearch 연결까지 UP이다")
    void health() {
        client.get().uri("/actuator/health")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    @DisplayName("검색 서비스에 없는 다른 도메인 API는 404다")
    void otherDomainApis() {
        client.get().uri("/api/v1/orders")
            .exchange()
            .expectStatus().isNotFound();
        client.get().uri("/api/v1/products/1")
            .exchange()
            .expectStatus().isNotFound();
    }
}
