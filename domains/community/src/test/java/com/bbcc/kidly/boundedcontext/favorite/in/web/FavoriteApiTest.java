package com.bbcc.kidly.boundedcontext.favorite.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.favorite.app.FavoriteErrorCode;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestMembers;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 찜 API의 HTTP 계약: PUT · DELETE 204(멱등), 404 · 409 · 400 · 401, 목록 · productIds 200, 요약 공개 200.
 */
@FunctionalTest
class FavoriteApiTest {

    private static final String PATH = "/api/v1/favorites";
    private static final long ME = 1L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void replicas() {
        jdbcTemplate.update("insert into community.replica_product (product_id, name, thumbnail_url, display_status, "
            + "source_occurred_at, created_at, updated_at) values (14, '상품 A', 'https://img/14.png', 'ON_SALE', "
            + "now(), now(), now()), (15, '판매 끝', null, 'SALE_ENDED', now(), now(), now())");
    }

    @Test
    @DisplayName("PUT 두 번 204 · DELETE 두 번 204, 그 사이 목록 · 요약이 맞다")
    void putDeleteIdempotent() {
        put("14").expectStatus().isNoContent();
        put("14").expectStatus().isNoContent();

        client.get().uri(PATH).headers(TestMembers.buyer(ME)).exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items.length()").isEqualTo(1)
            .jsonPath("$.items[0].productId").isEqualTo("14")
            .jsonPath("$.items[0].productName").isEqualTo("상품 A")
            .jsonPath("$.items[0].thumbnailUrl").isEqualTo("https://img/14.png")
            .jsonPath("$.items[0].displayStatus").isEqualTo("ON_SALE")
            .jsonPath("$.items[0].favoritedAt").isNotEmpty()
            .jsonPath("$").value(Map.class, body -> assertThat(body).containsEntry("nextCursor", null));
        client.get().uri(PATH + "/summary?productId=14").exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.productId").isEqualTo("14")
            .jsonPath("$.favoriteCount").isEqualTo(1);

        delete("14").expectStatus().isNoContent();
        delete("14").expectStatus().isNoContent();
        client.get().uri(PATH + "/summary?productId=14").exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.favoriteCount").isEqualTo(0);
    }

    @Test
    @DisplayName("productIds로 내가 찜한 것만 200")
    void productIds() {
        put("14").expectStatus().isNoContent();

        client.get().uri(PATH + "?productIds=14,15,16").headers(TestMembers.buyer(ME)).exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items.length()").isEqualTo(1)
            .jsonPath("$.items[0].productId").isEqualTo("14");
    }

    @Test
    @DisplayName("오류: 복사본 없음 404, 판매 종료 409, 상품 ID · productIds · size · cursor 400, 로그인 없음 401(PUT · DELETE · GET)")
    void errors() {
        expectProblem(put("999"), 404, FavoriteErrorCode.FAVORITE_PRODUCT_NOT_FOUND);
        expectProblem(put("15"), 409, FavoriteErrorCode.FAVORITE_PRODUCT_SALE_ENDED);
        expectProblem(put("abc"), 400, FavoriteErrorCode.INVALID_PRODUCT_ID);
        expectProblem(client.get().uri(PATH + "?productIds=1&cursor=abc").headers(TestMembers.buyer(ME)).exchange(),
            400, FavoriteErrorCode.INVALID_PRODUCT_IDS);
        expectProblem(client.get().uri(PATH + "?productIds=").headers(TestMembers.buyer(ME)).exchange(),
            400, FavoriteErrorCode.INVALID_PRODUCT_IDS);
        expectProblem(client.get().uri(PATH + "?size=51").headers(TestMembers.buyer(ME)).exchange(),
            400, FavoriteErrorCode.INVALID_PAGE_SIZE);
        expectProblem(client.get().uri(PATH + "?cursor=not-a-cursor").headers(TestMembers.buyer(ME)).exchange(),
            400, FavoriteErrorCode.INVALID_CURSOR);
        client.put().uri(PATH + "/14").exchange().expectStatus().isUnauthorized();
        client.delete().uri(PATH + "/14").exchange().expectStatus().isUnauthorized();
        client.get().uri(PATH).exchange().expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("요약은 로그인 없이 200, 찜이 없으면 0")
    void summaryPublic() {
        client.get().uri(PATH + "/summary?productId=999").exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.productId").isEqualTo("999")
            .jsonPath("$.favoriteCount").isEqualTo(0);
        expectProblem(client.get().uri(PATH + "/summary").exchange(), 400, FavoriteErrorCode.INVALID_PRODUCT_ID);
    }

    private RestTestClient.ResponseSpec put(String productId) {
        return client.put().uri(PATH + "/" + productId).headers(TestMembers.buyer(ME)).exchange();
    }

    private RestTestClient.ResponseSpec delete(String productId) {
        return client.delete().uri(PATH + "/" + productId).headers(TestMembers.buyer(ME)).exchange();
    }

    private static void expectProblem(RestTestClient.ResponseSpec response, int status, FavoriteErrorCode code) {
        response.expectStatus().isEqualTo(status)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(status)
            .jsonPath("$.title").isEqualTo(code.title());
    }
}
