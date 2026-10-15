package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductListFixture.CREATED_AT;
import static com.bbcc.kidly.boundedcontext.product.ProductListFixture.document;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocument;
import com.bbcc.kidly.support.FunctionalTest;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

    private void persist(ProductSearchDocument... documents) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (ProductSearchDocument document : documents) {
                entityManager.persist(document);
            }
        });
    }
}
