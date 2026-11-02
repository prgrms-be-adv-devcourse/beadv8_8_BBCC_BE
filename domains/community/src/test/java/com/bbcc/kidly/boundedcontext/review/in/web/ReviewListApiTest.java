package com.bbcc.kidly.boundedcontext.review.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.ReviewErrorCode;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestMembers;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 리뷰 목록 · 요약 API의 HTTP 계약: 상품 목록 · 요약은 로그인 없이 200, 내 리뷰는 로그인 필요(401), productId 오류 400.
 * /me · /summary · /eligible은 /{reviewId}(PATCH · DELETE)와 겹치지 않는다.
 */
@FunctionalTest
class ReviewListApiTest {

    private static final String PATH = "/api/v1/reviews";
    private static final long ME = 1L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 1, 10, 0, 0, 123_456_000);

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void data() {
        jdbcTemplate.update("insert into community.replica_product (product_id, name, thumbnail_url, display_status, "
            + "source_occurred_at, created_at, updated_at) values (14, '상품 A', 'https://img/14.png', 'SALE_ENDED', "
            + "?, ?, ?)", CREATED_AT, CREATED_AT, CREATED_AT);
        jdbcTemplate.update("insert into community.review (order_item_id, product_id, member_id, nickname, rating, "
            + "content, created_at, updated_at) values (11, 14, ?, '닉', 4, '좋아요', ?, ?)",
            ME, CREATED_AT, CREATED_AT);
        jdbcTemplate.update("insert into community.review_stats (product_id, review_count, rating_sum, created_at, "
            + "updated_at) values (14, 3, 13, ?, ?)", CREATED_AT, CREATED_AT);
    }

    @Test
    @DisplayName("상품 리뷰 목록은 로그인 없이 200이고 응답 모양(ID 문자열 · 상품 정보 · 표시 상태)을 지킨다")
    void productListPublic() {
        client.get().uri(PATH + "?productId=14")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items.length()").isEqualTo(1)
            .jsonPath("$.items[0].reviewId").isNotEmpty()
            .jsonPath("$.items[0].orderItemId").isEqualTo("11")
            .jsonPath("$.items[0].productId").isEqualTo("14")
            .jsonPath("$.items[0].productName").isEqualTo("상품 A")
            .jsonPath("$.items[0].thumbnailUrl").isEqualTo("https://img/14.png")
            .jsonPath("$.items[0].displayStatus").isEqualTo("SALE_ENDED")
            .jsonPath("$.items[0].rating").isEqualTo(4)
            .jsonPath("$.items[0].content").isEqualTo("좋아요")
            .jsonPath("$.items[0].nickname").isEqualTo("닉")
            .jsonPath("$.items[0].createdAt").isEqualTo("2026-11-01T10:00:00.123456")
            .jsonPath("$.items[0].updatedAt").isEqualTo("2026-11-01T10:00:00.123456")
            .jsonPath("$.items[0].memberId").doesNotExist()
            .jsonPath("$").value(Map.class, body -> assertThat(body).containsEntry("nextCursor", null));
    }

    @Test
    @DisplayName("내 리뷰는 로그인하면 200, 로그인 없으면 401")
    void myReviews() {
        client.get().uri(PATH + "/me")
            .headers(TestMembers.buyer(ME))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items[0].orderItemId").isEqualTo("11");
        client.get().uri(PATH + "/me")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("요약은 로그인 없이 200: 리뷰 수 · 평균(JSON 숫자), 리뷰 없는 상품은 0 · null")
    void summary() {
        client.get().uri(PATH + "/summary?productId=14")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.productId").isEqualTo("14")
            .jsonPath("$.reviewCount").isEqualTo(3)
            .jsonPath("$.averageRating").isEqualTo(4.3);
        client.get().uri(PATH + "/summary?productId=999")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.productId").isEqualTo("999")
            .jsonPath("$.reviewCount").isEqualTo(0)
            .jsonPath("$.averageRating").isEmpty();
    }

    @Test
    @DisplayName("요약 응답의 productId는 요청 문자열이 아니라 파싱한 값이다(014 → \"14\")")
    void summaryProductIdNormalized() {
        client.get().uri(PATH + "/summary?productId=014")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.productId").isEqualTo("14")
            .jsonPath("$.reviewCount").isEqualTo(3);
    }

    @Test
    @DisplayName("productId가 없거나 숫자가 아니면 400 INVALID_PRODUCT_ID(목록 · 요약)")
    void invalidProductId() {
        String[] uris = {PATH, PATH + "?productId=abc", PATH + "/summary", PATH + "/summary?productId=0"};
        for (String uri : uris) {
            client.get().uri(uri)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.title").isEqualTo(ReviewErrorCode.INVALID_PRODUCT_ID.title());
        }
    }

    @Test
    @DisplayName("/me · /summary · /eligible GET은 /{reviewId}(PATCH · DELETE)와 겹치지 않는다")
    void pathsDoNotCollide() {
        client.get().uri(PATH + "/eligible").headers(TestMembers.buyer(ME)).exchange().expectStatus().isOk();
        client.get().uri(PATH + "/summary?productId=14").exchange().expectStatus().isOk();
        client.get().uri(PATH + "/me").headers(TestMembers.buyer(ME)).exchange().expectStatus().isOk();
        client.delete().uri(PATH + "/999999").headers(TestMembers.buyer(ME)).exchange()
            .expectStatus().isNotFound();
    }
}
