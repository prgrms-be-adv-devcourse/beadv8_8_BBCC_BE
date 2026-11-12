package com.bbcc.kidly.boundedcontext.review.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.ReviewErrorCode;
import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationService;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Result;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationReason;
import com.bbcc.kidly.boundedcontext.review.out.moderation.FakeModerationAdapter;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestAdmins;
import com.bbcc.kidly.global.security.TestMembers;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 리뷰 검사 HTTP 계약: 공개 목록 · 요약에서 숨김 제외, 내 리뷰 visibility, 관리자 목록 · 숨김 확정 · 복원.
 */
@FunctionalTest
class ReviewModerationApiTest {

    private static final String REVIEWS = "/api/v1/reviews";
    private static final String ADMIN_REVIEWS = "/api/v1/admin/community/reviews";
    private static final long ME = 1L;
    private static final long OTHER = 2L;
    private static final long ADMIN = 99L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReviewModerationService moderationService;

    @Autowired
    private FakeModerationAdapter fake;

    private String mine;
    private String others;

    @BeforeEach
    void reviews() {
        fake.reset();
        mine = write(ME, 11, 5);
        others = write(OTHER, 12, 3);
    }

    @Test
    @DisplayName("검사가 숨긴 리뷰는 공개 목록 · 요약에서 빠지고, 내 리뷰에는 visibility UNDER_REVIEW(이유 없이)로 보인다")
    void hiddenExcludedFromPublic() {
        fake.willReturn(Result.flagged(ModerationReason.HATE), Result.passed());
        moderationService.processPending();

        client.get().uri(REVIEWS + "?productId=14").exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items.length()").isEqualTo(1)
            .jsonPath("$.items[0].reviewId").isEqualTo(others)
            .jsonPath("$.items[0].visibility").isEqualTo("PUBLIC");
        client.get().uri(REVIEWS + "/summary?productId=14").exchange()
            .expectBody()
            .jsonPath("$.reviewCount").isEqualTo(1)
            .jsonPath("$.averageRating").isEqualTo(3.0);
        Map<?, ?> page = client.get().uri(REVIEWS + "/me").headers(TestMembers.buyer(ME)).exchange()
            .expectStatus().isOk().returnResult(Map.class).getResponseBody();
        Map<?, ?> item = (Map<?, ?>) ((List<?>) page.get("items")).getFirst();
        assertThat(item.get("reviewId")).isEqualTo(mine);
        assertThat(item.get("visibility")).isEqualTo("UNDER_REVIEW");
        assertThat(item.toString()).doesNotContain("HATE").doesNotContain("HIDDEN");
    }

    @Test
    @DisplayName("관리자 목록: 상태별(검사 이유 · 시도 · 본문 · 사진 포함), 상태가 없거나 틀리면 400 INVALID_MODERATION_STATUS")
    void adminList() {
        fake.willReturn(Result.flagged(ModerationReason.HATE), Result.passed());
        moderationService.processPending();

        client.get().uri(ADMIN_REVIEWS + "?status=HIDDEN").headers(TestAdmins.of(ADMIN)).exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items.length()").isEqualTo(1)
            .jsonPath("$.items[0].reviewId").isEqualTo(mine)
            .jsonPath("$.items[0].productId").isEqualTo("14")
            .jsonPath("$.items[0].status").isEqualTo("HIDDEN")
            .jsonPath("$.items[0].reason").isEqualTo("HATE")
            .jsonPath("$.items[0].attempts").isEqualTo(0)
            .jsonPath("$.items[0].attemptsExhausted").isEqualTo(false)
            .jsonPath("$.items[0].content").isEqualTo("리뷰 " + ME)
            .jsonPath("$.items[0].imageUrls").isEqualTo(List.of())
            .jsonPath("$.items[0].moderatedAt").isNotEmpty()
            .jsonPath("$.nextCursor").doesNotExist();
        client.get().uri(ADMIN_REVIEWS + "?status=PASSED").headers(TestAdmins.of(ADMIN)).exchange()
            .expectBody()
            .jsonPath("$.items[0].reviewId").isEqualTo(others);

        for (String query : List.of("", "?status=", "?status=hidden", "?status=DELETED")) {
            client.get().uri(ADMIN_REVIEWS + query).headers(TestAdmins.of(ADMIN)).exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.title").isEqualTo(ReviewErrorCode.INVALID_MODERATION_STATUS.title());
        }
    }

    @Test
    @DisplayName("관리자 목록 다음 쪽: size 1이면 최신(id 큰) 것부터 두 쪽, 마지막 쪽 커서는 없고, 다른 목록의 커서 · 깨진 커서는 400")
    void adminListPages() {
        visibility(mine, "HIDDEN").expectStatus().isOk();
        visibility(others, "HIDDEN").expectStatus().isOk();

        Map<?, ?> first = adminPage("?status=CONFIRMED_HIDDEN&size=1");
        assertThat(first.get("items")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
            .singleElement().satisfies(item -> assertThat(((Map<?, ?>) item).get("reviewId")).isEqualTo(others));
        assertThat(first.get("nextCursor")).isNotNull();

        Map<?, ?> second = adminPage("?status=CONFIRMED_HIDDEN&size=1&cursor=" + first.get("nextCursor"));
        assertThat(second.get("items")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
            .singleElement().satisfies(item -> assertThat(((Map<?, ?>) item).get("reviewId")).isEqualTo(mine));
        assertThat(second.get("nextCursor")).isNull();

        // 리뷰 목록 커서(작성 시각|id)는 관리자 목록 커서(id)가 아니다
        String reviewListCursor = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(("2026-11-06T10:00:00.123456|" + mine).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (String cursor : List.of("!!", reviewListCursor)) {
            client.get().uri(ADMIN_REVIEWS + "?status=CONFIRMED_HIDDEN&size=1&cursor=" + cursor)
                .headers(TestAdmins.of(ADMIN)).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.title").isEqualTo(ReviewErrorCode.INVALID_CURSOR.title());
        }
    }

    private Map<?, ?> adminPage(String query) {
        return client.get().uri(ADMIN_REVIEWS + query).headers(TestAdmins.of(ADMIN)).exchange()
            .expectStatus().isOk().returnResult(Map.class).getResponseBody();
    }

    @Test
    @DisplayName("관리자 숨김 확정 · 복원: 200 + 바뀐 상태, 다시 보내도 200, 공개 목록 · 요약에 반영, 없는 리뷰 404, 틀린 값 400")
    void adminVisibility() {
        visibility(mine, "HIDDEN").expectStatus().isOk()
            .expectBody()
            .jsonPath("$.reviewId").isEqualTo(mine)
            .jsonPath("$.status").isEqualTo("CONFIRMED_HIDDEN");
        visibility(mine, "HIDDEN").expectStatus().isOk()
            .expectBody().jsonPath("$.status").isEqualTo("CONFIRMED_HIDDEN");
        client.get().uri(REVIEWS + "/summary?productId=14").exchange()
            .expectBody().jsonPath("$.reviewCount").isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select moderated_by_admin_id from community.review where id = ?",
            Long.class, Long.valueOf(mine))).isEqualTo(ADMIN);

        visibility(mine, "VISIBLE").expectStatus().isOk()
            .expectBody().jsonPath("$.status").isEqualTo("RESTORED");
        visibility(mine, "VISIBLE").expectStatus().isOk();
        client.get().uri(REVIEWS + "/summary?productId=14").exchange()
            .expectBody().jsonPath("$.reviewCount").isEqualTo(2);

        visibility("999999", "HIDDEN").expectStatus().isNotFound()
            .expectBody().jsonPath("$.title").isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND.title());
        for (Object invalid : new Object[] {"hidden", "DELETED", "", 1}) {
            client.patch().uri(ADMIN_REVIEWS + "/" + mine + "/visibility").headers(TestAdmins.of(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("visibility", invalid)).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.title").isEqualTo(ReviewErrorCode.INVALID_VISIBILITY.title());
        }
        client.patch().uri(ADMIN_REVIEWS + "/" + mine + "/visibility").headers(TestAdmins.of(ADMIN))
            .contentType(MediaType.APPLICATION_JSON).body(Map.of()).exchange()
            .expectStatus().isBadRequest()
            .expectBody().jsonPath("$.title").isEqualTo(ReviewErrorCode.INVALID_VISIBILITY.title());
    }

    @Test
    @DisplayName("관리자 헤더가 없으면 401(목록 · 숨김), 숨김 리뷰를 남이 고치거나 지우면 403")
    void adminOnlyAndOthersForbidden() {
        client.get().uri(ADMIN_REVIEWS + "?status=HIDDEN").exchange().expectStatus().isUnauthorized();
        client.patch().uri(ADMIN_REVIEWS + "/" + mine + "/visibility").headers(TestMembers.buyer(ME))
            .contentType(MediaType.APPLICATION_JSON).body(Map.of("visibility", "VISIBLE")).exchange()
            .expectStatus().isUnauthorized();

        visibility(mine, "HIDDEN").expectStatus().isOk();
        client.patch().uri(REVIEWS + "/" + mine).headers(TestMembers.buyer(OTHER))
            .contentType(MediaType.APPLICATION_JSON).body(Map.of("rating", 1)).exchange()
            .expectStatus().isForbidden();
        client.delete().uri(REVIEWS + "/" + mine).headers(TestMembers.buyer(OTHER)).exchange()
            .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("검사 통과 · 관리자 숨김 확정 · 다시 공개 뒤에도 공개 목록 · 내 리뷰의 updatedAt은 작성 때 값 그대로다")
    void moderationKeepsUpdatedAt() {
        jdbcTemplate.update("update community.review set updated_at = '2026-11-01 10:00:00.123456'");
        fake.willReturn(Result.passed(), Result.passed());
        moderationService.processPending();
        visibility(mine, "HIDDEN").expectStatus().isOk();
        visibility(mine, "VISIBLE").expectStatus().isOk();

        client.get().uri(REVIEWS + "?productId=14").exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items[?(@.reviewId == '" + mine + "')].updatedAt").isEqualTo(
                List.of("2026-11-01T10:00:00.123456"));
        client.get().uri(REVIEWS + "/me").headers(TestMembers.buyer(ME)).exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items[0].updatedAt").isEqualTo("2026-11-01T10:00:00.123456");
    }

    private RestTestClient.ResponseSpec visibility(String reviewId, String visibility) {
        return client.patch().uri(ADMIN_REVIEWS + "/" + reviewId + "/visibility").headers(TestAdmins.of(ADMIN))
            .contentType(MediaType.APPLICATION_JSON).body(Map.of("visibility", visibility)).exchange();
    }

    private String write(long memberId, long orderItemId, int rating) {
        jdbcTemplate.update("insert into community.review_eligibility (order_item_id, order_id, member_id, product_id, "
            + "option_id, size, confirmed_at, created_at, updated_at) values (?, 12, ?, 14, 15, null, now(), now(), "
            + "now())", orderItemId, memberId);
        Map<?, ?> body = client.post().uri(REVIEWS).headers(TestMembers.buyer(memberId))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("orderItemId", String.valueOf(orderItemId), "rating", rating, "content", "리뷰 " + memberId))
            .exchange()
            .expectStatus().isCreated()
            .returnResult(Map.class).getResponseBody();
        return body.get("reviewId").toString();
    }
}
