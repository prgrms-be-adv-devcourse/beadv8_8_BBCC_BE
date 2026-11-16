package com.bbcc.kidly.boundedcontext.favorite.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.favorite.app.FavoriteErrorCode;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestChildren;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.auth.MemberHeaders;
import com.bbcc.kidly.shared.auth.PrincipalHeaders;
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
    private static final long GUARDIAN = 50L;

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

    @Test
    @DisplayName("자녀 토큰: PUT · DELETE 204, 목록은 자기 찜만(회원 1과 자녀 계정 1은 섞이지 않음), 요약은 회원 · 자녀 찜 합계")
    void childFavorites() {
        jdbcTemplate.update("insert into community.replica_product (product_id, name, thumbnail_url, display_status, "
            + "source_occurred_at, created_at, updated_at) values (16, '상품 C', null, 'ON_SALE', now(), now(), now())");
        client.put().uri(PATH + "/14").headers(TestChildren.of(ME, GUARDIAN)).exchange().expectStatus().isNoContent();
        client.put().uri(PATH + "/16").headers(TestChildren.of(ME, GUARDIAN)).exchange().expectStatus().isNoContent();
        put("14").expectStatus().isNoContent();

        client.get().uri(PATH).headers(TestChildren.of(ME, GUARDIAN)).exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items.length()").isEqualTo(2)
            .jsonPath("$.items[0].productId").isEqualTo("16")
            .jsonPath("$.items[1].productId").isEqualTo("14");
        client.get().uri(PATH).headers(TestMembers.buyer(ME)).exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items.length()").isEqualTo(1)
            .jsonPath("$.items[0].productId").isEqualTo("14");
        client.get().uri(PATH + "/summary?productId=14").exchange()
            .expectBody().jsonPath("$.favoriteCount").isEqualTo(2);

        client.delete().uri(PATH + "/14").headers(TestChildren.of(ME, GUARDIAN)).exchange()
            .expectStatus().isNoContent();
        client.get().uri(PATH + "?productIds=14,16").headers(TestChildren.of(ME, GUARDIAN)).exchange()
            .expectBody()
            .jsonPath("$.items.length()").isEqualTo(1)
            .jsonPath("$.items[0].productId").isEqualTo("16");
        client.get().uri(PATH + "?productIds=14,16").headers(TestMembers.buyer(ME)).exchange()
            .expectBody()
            .jsonPath("$.items.length()").isEqualTo(1)
            .jsonPath("$.items[0].productId").isEqualTo("14");
    }

    @Test
    @DisplayName("주체 헤더: 자녀 헤더가 하나뿐이거나 잘못되면 401, 회원 헤더가 있으면 회원으로 보고 잘못되면 401")
    void callerHeaders() {
        client.put().uri(PATH + "/14").header(PrincipalHeaders.CHILD_ACCOUNT_ID, "1").exchange()
            .expectStatus().isUnauthorized();
        client.put().uri(PATH + "/14").header(PrincipalHeaders.CHILD_GUARDIAN_ID, "50").exchange()
            .expectStatus().isUnauthorized();
        client.get().uri(PATH).header(PrincipalHeaders.CHILD_ACCOUNT_ID, "abc")
            .header(PrincipalHeaders.CHILD_GUARDIAN_ID, "50").exchange().expectStatus().isUnauthorized();
        client.get().uri(PATH).header(PrincipalHeaders.CHILD_ACCOUNT_ID, "0")
            .header(PrincipalHeaders.CHILD_GUARDIAN_ID, "50").exchange().expectStatus().isUnauthorized();
        client.get().uri(PATH).header(MemberHeaders.MEMBER_ID, "x").headers(TestChildren.of(ME, GUARDIAN)).exchange()
            .expectStatus().isUnauthorized();
        // 회원 헤더가 있으면 자녀 헤더보다 먼저 본다
        client.put().uri(PATH + "/14").headers(TestMembers.buyer(ME)).headers(TestChildren.of(2L, GUARDIAN))
            .exchange().expectStatus().isNoContent();
        assertThat(jdbcTemplate.queryForList("select owner_type || ':' || owner_id from community.favorite",
            String.class)).containsExactly("MEMBER:1");
    }

    @Test
    @DisplayName("지운 자녀 계정의 PUT · DELETE · GET은 403 FAVORITE_CHILD_ACCOUNT_DELETED, 같은 ID의 회원 · 다른 자녀 계정은 그대로")
    void deletedChildForbidden() {
        jdbcTemplate.update("insert into community.deleted_child_account (account_id, guardian_id, deleted_at, "
            + "created_at) values (?, ?, now(), now())", ME, GUARDIAN);

        expectProblem(client.put().uri(PATH + "/14").headers(TestChildren.of(ME, GUARDIAN)).exchange(), 403,
            FavoriteErrorCode.FAVORITE_CHILD_ACCOUNT_DELETED);
        expectProblem(client.delete().uri(PATH + "/14").headers(TestChildren.of(ME, GUARDIAN)).exchange(), 403,
            FavoriteErrorCode.FAVORITE_CHILD_ACCOUNT_DELETED);
        expectProblem(client.get().uri(PATH).headers(TestChildren.of(ME, GUARDIAN)).exchange(), 403,
            FavoriteErrorCode.FAVORITE_CHILD_ACCOUNT_DELETED);
        put("14").expectStatus().isNoContent();
        client.put().uri(PATH + "/14").headers(TestChildren.of(ME + 1, GUARDIAN)).exchange()
            .expectStatus().isNoContent();
        assertThat(jdbcTemplate.queryForObject("select count(*) from community.favorite where owner_type = 'CHILD' "
            + "and owner_id = ?", Long.class, ME)).isZero();
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
