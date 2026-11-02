package com.bbcc.kidly.boundedcontext.review.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.ReviewErrorCode;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.auth.Role;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 리뷰 작성 · 수정 · 삭제 API의 HTTP 계약: 201 · Location · 200 · 204 · 401 · 400 · 403 · 404 · 409.
 */
@FunctionalTest
class ReviewApiTest {

    private static final String PATH = "/api/v1/reviews";
    private static final long ME = 1L;
    private static final long OTHER = 2L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void eligibility() {
        jdbcTemplate.update("insert into community.review_eligibility (order_item_id, order_id, member_id, product_id, "
            + "option_id, size, confirmed_at, created_at, updated_at) values (11, 12, ?, 14, 15, '110', now(), now(), "
            + "now())", ME);
    }

    @Test
    @DisplayName("작성 201 · Location · 응답 모양(ID 문자열, 작성 시점 닉네임), 같은 주문상품에 또 쓰면 409")
    void writeThenConflict() {
        client.post().uri(PATH)
            .headers(TestMembers.buyer(ME))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("orderItemId", "11", "rating", 5, "content", " 좋아요 "))
            .exchange()
            .expectStatus().isCreated()
            .expectHeader().valueMatches("Location", "/api/v1/reviews/\\d+")
            .expectBody()
            .jsonPath("$.reviewId").isNotEmpty()
            .jsonPath("$.orderItemId").isEqualTo("11")
            .jsonPath("$.productId").isEqualTo("14")
            .jsonPath("$.rating").isEqualTo(5)
            .jsonPath("$.content").isEqualTo("좋아요")
            .jsonPath("$.nickname").isEqualTo(TestMembers.nickname(ME))
            .jsonPath("$.createdAt").isNotEmpty()
            .jsonPath("$.updatedAt").isNotEmpty();

        client.post().uri(PATH)
            .headers(TestMembers.buyer(ME))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("orderItemId", "11", "rating", 4, "content", "또"))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo(ReviewErrorCode.REVIEW_ALREADY_WRITTEN.title());
    }

    @Test
    @DisplayName("수정 200(별점 · 본문 반영), 삭제 204, 지운 리뷰를 고치면 404")
    void updateDeleteNotFound() {
        String reviewId = write();

        client.patch().uri(PATH + "/" + reviewId)
            .headers(TestMembers.buyer(ME))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("rating", 2, "content", "고쳤어요"))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.reviewId").isEqualTo(reviewId)
            .jsonPath("$.rating").isEqualTo(2)
            .jsonPath("$.content").isEqualTo("고쳤어요");

        client.delete().uri(PATH + "/" + reviewId)
            .headers(TestMembers.buyer(ME))
            .exchange()
            .expectStatus().isNoContent();

        client.patch().uri(PATH + "/" + reviewId)
            .headers(TestMembers.buyer(ME))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("rating", 3, "content", "다시"))
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.title").isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND.title());
    }

    @Test
    @DisplayName("로그인 없이 쓰면 401")
    void unauthorized() {
        client.post().uri(PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("orderItemId", "11", "rating", 5, "content", "좋아요"))
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("별점이 1~5 밖이면 400 INVALID_RATING")
    void invalidRating() {
        client.post().uri(PATH)
            .headers(TestMembers.buyer(ME))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("orderItemId", "11", "rating", 6, "content", "좋아요"))
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo(ReviewErrorCode.INVALID_RATING.title());
    }

    @Test
    @DisplayName("내 자격이 없는 주문상품이면 403 REVIEW_NOT_ELIGIBLE, 남의 리뷰를 지우면 403 REVIEW_FORBIDDEN")
    void forbidden() {
        client.post().uri(PATH)
            .headers(TestMembers.buyer(OTHER))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("orderItemId", "11", "rating", 5, "content", "남의 주문상품"))
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.title").isEqualTo(ReviewErrorCode.REVIEW_NOT_ELIGIBLE.title());

        String reviewId = write();
        client.delete().uri(PATH + "/" + reviewId)
            .headers(TestMembers.buyer(OTHER))
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.title").isEqualTo(ReviewErrorCode.REVIEW_FORBIDDEN.title());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRatings")
    @DisplayName("작성: 별점이 정수 1~5가 아니면(소수 · 문자열 · 참거짓 · null) 400 INVALID_RATING이고 저장하지 않는다")
    void writeRejectsNonIntegerRating(String name, Object rating) {
        Map<String, Object> body = new HashMap<>();
        body.put("orderItemId", "11");
        body.put("rating", rating);
        body.put("content", "좋아요");

        expectProblem(client.post().uri(PATH).headers(TestMembers.buyer(ME)).contentType(MediaType.APPLICATION_JSON)
            .body(body).exchange(), 400, ReviewErrorCode.INVALID_RATING);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
            "select count(*) from community.review", Integer.class)).isZero();
    }

    private static Stream<Arguments> invalidRatings() {
        return Stream.of(Arguments.of("4.5", 4.5), Arguments.of("문자열 \"5\"", "5"), Arguments.of("true", true),
            Arguments.of("null", null));
    }

    @Test
    @DisplayName("수정: 별점 4.5는 400 INVALID_RATING이고 값은 그대로다")
    void updateRejectsFractionalRating() {
        String reviewId = write();

        expectProblem(client.patch().uri(PATH + "/" + reviewId).headers(TestMembers.buyer(ME))
            .contentType(MediaType.APPLICATION_JSON).body(Map.of("rating", 4.5, "content", "고침")).exchange(),
            400, ReviewErrorCode.INVALID_RATING);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
            "select rating from community.review where id = ?", Integer.class, Long.valueOf(reviewId))).isEqualTo(5);
    }

    @Test
    @DisplayName("별점 4.0처럼 소수부가 없는 수는 정수 4로 받는다")
    void wholeNumberRatingAccepted() {
        client.post().uri(PATH).headers(TestMembers.buyer(ME)).contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("orderItemId", "11", "rating", 4.0, "content", "좋아요"))
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.rating").isEqualTo(4);
    }

    @Test
    @DisplayName("본문이 공백뿐이면 400 INVALID_CONTENT, 주문상품 ID가 숫자가 아니면 400 INVALID_ORDER_ITEM_ID")
    void invalidContentAndOrderItemId() {
        expectProblem(client.post().uri(PATH).headers(TestMembers.buyer(ME)).contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("orderItemId", "11", "rating", 5, "content", "   ")).exchange(),
            400, ReviewErrorCode.INVALID_CONTENT);
        expectProblem(client.post().uri(PATH).headers(TestMembers.buyer(ME)).contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("orderItemId", "abc", "rating", 5, "content", "좋아요")).exchange(),
            400, ReviewErrorCode.INVALID_ORDER_ITEM_ID);
    }

    @Test
    @DisplayName("남의 리뷰를 고치면 403 REVIEW_FORBIDDEN, 없는 리뷰를 지우면 404 REVIEW_NOT_FOUND")
    void patchForbiddenAndDeleteNotFound() {
        String reviewId = write();

        expectProblem(client.patch().uri(PATH + "/" + reviewId).headers(TestMembers.buyer(OTHER))
            .contentType(MediaType.APPLICATION_JSON).body(Map.of("rating", 1, "content", "남의 수정")).exchange(),
            403, ReviewErrorCode.REVIEW_FORBIDDEN);
        expectProblem(client.delete().uri(PATH + "/999999").headers(TestMembers.buyer(ME)).exchange(),
            404, ReviewErrorCode.REVIEW_NOT_FOUND);
    }

    @Test
    @DisplayName("토큰에 닉네임이 없으면 닉네임 null로 작성된다")
    void writeWithoutNickname() {
        client.post().uri(PATH).headers(TestMembers.of(ME, null, Role.BUYER)).contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("orderItemId", "11", "rating", 5, "content", "좋아요"))
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.nickname").doesNotExist();
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
            "select nickname from community.review where order_item_id = 11", String.class)).isNull();
    }

    @Test
    @DisplayName("리뷰 생성 · 수정 시각은 마이크로초로 저장되고, 응답 값은 DB 값과 같다")
    void timesStoredInMicrosAndEchoed() {
        Map<?, ?> body = client.post().uri(PATH).headers(TestMembers.buyer(ME)).contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("orderItemId", "11", "rating", 5, "content", "좋아요"))
            .exchange()
            .expectStatus().isCreated()
            .returnResult(Map.class)
            .getResponseBody();
        LocalDateTime createdAt = LocalDateTime.parse(body.get("createdAt").toString());
        LocalDateTime updatedAt = LocalDateTime.parse(body.get("updatedAt").toString());
        Map<String, Object> row = jdbcTemplate.queryForMap("select created_at, updated_at from community.review "
            + "where id = ?", Long.valueOf(body.get("reviewId").toString()));

        assertThat(createdAt.getNano() % 1_000).isZero();
        assertThat(updatedAt.getNano() % 1_000).isZero();
        assertThat(createdAt).isEqualTo(((java.sql.Timestamp) row.get("created_at")).toLocalDateTime());
        assertThat(updatedAt).isEqualTo(((java.sql.Timestamp) row.get("updated_at")).toLocalDateTime());
    }

    private static void expectProblem(RestTestClient.ResponseSpec response, int status, ReviewErrorCode code) {
        response.expectStatus().isEqualTo(status)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(status)
            .jsonPath("$.title").isEqualTo(code.title());
    }

    private String write() {
        return client.post().uri(PATH)
            .headers(TestMembers.buyer(ME))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("orderItemId", "11", "rating", 5, "content", "좋아요"))
            .exchange()
            .expectStatus().isCreated()
            .returnResult(Map.class)
            .getResponseBody()
            .get("reviewId").toString();
    }
}
