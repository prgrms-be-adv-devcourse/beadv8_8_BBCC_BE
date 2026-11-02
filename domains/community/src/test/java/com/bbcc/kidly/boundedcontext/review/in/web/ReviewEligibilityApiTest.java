package com.bbcc.kidly.boundedcontext.review.in.web;

import com.bbcc.kidly.boundedcontext.review.app.ReviewErrorCode;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestMembers;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 리뷰 작성 자격 목록 API의 HTTP 계약: 로그인 200 · 응답 모양(ID 문자열), 로그인 없음 401, 잘못된 size · cursor 400.
 */
@FunctionalTest
class ReviewEligibilityApiTest {

    private static final String PATH = "/api/v1/reviews/eligible";
    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 11, 1, 10, 0);

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("로그인하면 200: 내 자격 목록, ID는 문자열, 상품 정보는 복사본에서, 마지막 쪽은 nextCursor null")
    void listsMine() {
        jdbcTemplate.update("insert into community.replica_product (product_id, name, thumbnail_url, display_status, "
            + "source_occurred_at, created_at, updated_at) values (14, '상품 A', 'https://img/14.png', 'ON_SALE', "
            + "now(), now(), now())");
        jdbcTemplate.update("insert into community.review_eligibility (order_item_id, order_id, member_id, "
            + "product_id, option_id, size, confirmed_at, created_at, updated_at) "
            + "values (11, 12, 1, 14, 15, '110', ?, now(), now())", CONFIRMED_AT);

        client.get().uri(PATH)
            .headers(TestMembers.buyer(1L))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items.length()").isEqualTo(1)
            .jsonPath("$.items[0].orderItemId").isEqualTo("11")
            .jsonPath("$.items[0].orderId").isEqualTo("12")
            .jsonPath("$.items[0].productId").isEqualTo("14")
            .jsonPath("$.items[0].productName").isEqualTo("상품 A")
            .jsonPath("$.items[0].thumbnailUrl").isEqualTo("https://img/14.png")
            .jsonPath("$.items[0].size").isEqualTo("110")
            .jsonPath("$.items[0].confirmedAt").isEqualTo("2026-11-01T10:00:00")
            .jsonPath("$.nextCursor").isEmpty();
    }

    @Test
    @DisplayName("로그인 헤더가 없으면 401")
    void unauthorized() {
        client.get().uri(PATH)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("size가 범위 밖이면 400 ProblemDetail(application/problem+json, INVALID_PAGE_SIZE)")
    void invalidSize() {
        client.get().uri(PATH + "?size=51")
            .headers(TestMembers.buyer(1L))
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(400)
            .jsonPath("$.title").isEqualTo(ReviewErrorCode.INVALID_PAGE_SIZE.title())
            .jsonPath("$.detail").isEqualTo(ReviewErrorCode.INVALID_PAGE_SIZE.detail());
    }

    @Test
    @DisplayName("cursor를 읽을 수 없으면 400 ProblemDetail(application/problem+json, INVALID_CURSOR)")
    void invalidCursor() {
        client.get().uri(PATH + "?cursor=not-a-cursor")
            .headers(TestMembers.buyer(1L))
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(400)
            .jsonPath("$.title").isEqualTo(ReviewErrorCode.INVALID_CURSOR.title())
            .jsonPath("$.detail").isEqualTo(ReviewErrorCode.INVALID_CURSOR.detail());
    }
}
