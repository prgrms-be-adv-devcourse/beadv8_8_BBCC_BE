package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductSearchFixture.CREATED_AT;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchFixture.document;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchFixture.value;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocument;
import com.bbcc.kidly.support.FunctionalTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 구매자 상품 목록 API를 HTTP 요청부터 응답까지 확인한다. 목록은 검색용 표만 읽으므로 테스트도 검색용 표에 행을 넣는다.
 */
@FunctionalTest
class ProductListApiTest {

    private static final String PATH = "/api/v1/products";

    @Autowired
    private RestTestClient client;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("로그인 없이 상품 목록을 받는다. 카드의 ID는 문자열이고, 없는 썸네일 · 상호명은 칸을 빼지 않고 null로 보낸다")
    void listProducts() {
        persist(
            document(1L).name("아기 맨투맨").price(25_000L).build(),
            document(2L).name("품절 바지").thumbnailUrl("https://cdn.kidly.test/2.jpg").price(31_000L)
                .soldOut().sizeFree().createdAt(CREATED_AT.plusDays(1)).build());

        client.get().uri(PATH)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").value(Map.class, body -> assertThat(body)
                .containsOnlyKeys("content", "page", "size", "totalElements", "totalPages", "hasNext")
                .containsEntry("page", 0)
                .containsEntry("size", 20)
                .containsEntry("totalElements", 2)
                .containsEntry("totalPages", 1)
                .containsEntry("hasNext", false))
            // jsonPath의 isEqualTo는 숫자도 문자열로 바꿔 비교하므로 Map으로 꺼내 값의 타입까지 확인한다
            .jsonPath("$.content[0]").value(Map.class, card -> assertThat(card)
                .hasSize(7)
                .containsEntry("productId", "2")
                .containsEntry("name", "품절 바지")
                .containsEntry("thumbnailUrl", "https://cdn.kidly.test/2.jpg")
                .containsEntry("businessName", null)
                .containsEntry("minPrice", 31_000)
                .containsEntry("soldOut", true)
                .containsEntry("sizeFree", true))
            .jsonPath("$.content[1]").value(Map.class, card -> assertThat(card)
                .hasSize(7)
                .containsEntry("productId", "1")
                .containsEntry("thumbnailUrl", null)
                .containsEntry("businessName", null)
                .containsEntry("minPrice", 25_000)
                .containsEntry("soldOut", false)
                .containsEntry("sizeFree", false));
    }

    @Test
    @DisplayName("조건에 맞는 상품이 없으면 404가 아니라 content가 빈 목록인 200을 받는다")
    void emptyList() {
        persist(document(1L).categoryId(1L).build());

        client.get().uri(PATH + "?categoryId={categoryId}", 99)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isArray()
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(0)
            .jsonPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("카테고리 · 키워드 · 항목 값 · 정렬 · 페이지 쿼리를 함께 보내면 모두 조회에 반영된다")
    void passQueryToSearch() {
        persist(
            document(1L).categoryId(1L).name("네이비 맨투맨").price(20_000L)
                .values(value("COLOR", 3L), value("SIZE", 15L)).build(),
            document(2L).categoryId(1L).name("네이비 맨투맨 세트").price(30_000L)
                .values(value("COLOR", 3L), value("SIZE", 15L)).createdAt(CREATED_AT.plusDays(1)).build(),
            document(3L).categoryId(2L).name("네이비 맨투맨").price(10_000L)
                .values(value("COLOR", 3L), value("SIZE", 15L)).createdAt(CREATED_AT.plusDays(2)).build(),
            document(4L).categoryId(1L).name("블랙 맨투맨").price(10_000L)
                .values(value("COLOR", 5L), value("SIZE", 15L)).createdAt(CREATED_AT.plusDays(3)).build(),
            document(5L).categoryId(1L).name("네이비 바지").price(10_000L)
                .values(value("COLOR", 3L), value("SIZE", 15L)).createdAt(CREATED_AT.plusDays(4)).build());

        // 조건에 맞는 상품은 1(20,000원)과 2(30,000원)다. 가격 낮은순의 첫 페이지(크기 1)라 상품 1만 온다
        client.get()
            .uri(PATH + "?categoryId={categoryId}&keyword={keyword}&attributes={color}&attributes={size}"
                + "&sort={sort}&page=0&size=1", 1, "맨투맨", "COLOR:3", "SIZE:15", "PRICE_ASC")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[*].productId").isEqualTo(List.of("1"))
            .jsonPath("$.size").isEqualTo(1)
            .jsonPath("$.totalElements").isEqualTo(2)
            .jsonPath("$.hasNext").isEqualTo(true);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "'size=0', 'size: 페이지 크기는 1 이상 100 이하여야 합니다.'",
        "'size=101', 'size: 페이지 크기는 1 이상 100 이하여야 합니다.'",
        "'page=-1', 'page: 페이지는 0 이상이어야 합니다.'",
        "'sort=POPULAR', 'sort: 정렬은 NEWEST, PRICE_ASC, PRICE_DESC 중 하나여야 합니다.'",
        "'attributes=COLOR', 'attributes[0]: 항목 값은 항목코드:값ID 형식이어야 합니다.'",
        "'attributes=COLOR:abc', 'attributes[0]: 항목 값은 항목코드:값ID 형식이어야 합니다.'",
        "'attributes=:3', 'attributes[0]: 항목 값은 항목코드:값ID 형식이어야 합니다.'",
        "'attributes=COLOR:99999999999999999999', 'attributes[0]: 항목 값은 항목코드:값ID 형식이어야 합니다.'",
        "'keyword=가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가', 'keyword: 검색어는 50자 이하여야 합니다.'"})
    @DisplayName("쿼리 값이 범위나 형식을 벗어나면 400 ProblemDetail과 칸 이름 · 사유를 담은 detail을 받는다")
    void rejectInvalidQuery(String query, String detail) {
        client.get().uri(PATH + "?" + query)
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").isEqualTo("about:blank")
            .jsonPath("$.title").isEqualTo("잘못된 요청입니다")
            .jsonPath("$.status").isEqualTo(400)
            .jsonPath("$.detail").isEqualTo(detail)
            .jsonPath("$.instance").isEqualTo(PATH);
    }

    @Test
    @DisplayName("로그인 없이 부를 수 있는 API여도 잘못된 토큰을 보내면 401을 받는다")
    void rejectInvalidToken() {
        client.get().uri(PATH)
            .headers(headers -> headers.setBearerAuth("invalid-token"))
            .exchange()
            .expectStatus().isUnauthorized();
    }

    private void persist(ProductSearchDocument... documents) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (ProductSearchDocument document : documents) {
                entityManager.persist(document);
            }
        });
    }
}
