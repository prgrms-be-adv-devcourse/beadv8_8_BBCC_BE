package com.bbcc.kidly.boundedcontext.review.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.ReviewErrorCode;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageStorage;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestMembers;
import java.util.ArrayList;
import java.util.HashMap;
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
 * 사진 리뷰 HTTP 계약: 업로드 주소 발급(200 · 401 · 400), 작성 · 수정의 imageUrls, 응답 · 목록 항목의 imageUrls.
 */
@FunctionalTest
class ReviewImageApiTest {

    private static final String REVIEWS = "/api/v1/reviews";
    private static final String UPLOAD_URLS = REVIEWS + "/images/upload-urls";
    private static final long ME = 1L;
    private static final long OTHER = 2L;
    private static final long TEN_MB = 10_485_760L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReviewImageStorage imageStorage;

    @BeforeEach
    void eligibility() {
        jdbcTemplate.update("insert into community.review_eligibility (order_item_id, order_id, member_id, product_id, "
            + "option_id, size, confirmed_at, created_at, updated_at) values (11, 12, ?, 14, 15, '110', now(), now(), "
            + "now())", ME);
    }

    @Test
    @DisplayName("업로드 주소 발급 200: 요청 순서대로 서명 주소 · 본인 경로의 공개 주소 · 만료 시각")
    void issueUploadUrls() {
        Map<?, ?> body = client.post().uri(UPLOAD_URLS).headers(TestMembers.buyer(ME))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("files", List.of(file("image/jpeg", 100), file("image/webp", TEN_MB))))
            .exchange()
            .expectStatus().isOk()
            .returnResult(Map.class)
            .getResponseBody();

        List<?> files = (List<?>) body.get("files");
        assertThat(files).hasSize(2);
        Map<?, ?> first = (Map<?, ?>) files.get(0);
        assertThat((String) first.get("imageUrl")).startsWith(imageStorage.ownImageUrlPrefix(ME)).endsWith(".jpg");
        assertThat((String) ((Map<?, ?>) files.get(1)).get("imageUrl")).endsWith(".webp");
        String key = ((String) first.get("imageUrl")).substring(imageStorage.ownImageUrlPrefix(ME).length());
        assertThat((String) first.get("uploadUrl")).contains(key).contains("X-Amz-Signature=");
        assertThat(first.get("expiresAt")).isNotNull();
    }

    @Test
    @DisplayName("업로드 주소 발급: 로그인 없으면 401, 형식 · 크기 · 빈 목록은 INVALID_IMAGE_FILE, 장수 초과는 TOO_MANY_IMAGES")
    void issueErrors() {
        client.post().uri(UPLOAD_URLS).contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("files", List.of(file("image/png", 1))))
            .exchange()
            .expectStatus().isUnauthorized();

        expectProblem(upload(Map.of("files", List.of(file("image/gif", 1)))), ReviewErrorCode.INVALID_IMAGE_FILE);
        expectProblem(upload(Map.of("files", List.of(file("image/png", TEN_MB + 1)))),
            ReviewErrorCode.INVALID_IMAGE_FILE);
        expectProblem(upload(Map.of("files", List.of())), ReviewErrorCode.INVALID_IMAGE_FILE);
        expectProblem(upload(Map.of()), ReviewErrorCode.INVALID_IMAGE_FILE);
        List<Map<String, Object>> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            six.add(file("image/png", 1));
        }
        upload(Map.of("files", six)).expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.title").isEqualTo(ReviewErrorCode.TOO_MANY_IMAGES.title())
            .jsonPath("$.detail").value(detail -> assertThat((String) detail).contains("5장"));
    }

    @Test
    @DisplayName("작성 201: imageUrls를 보낸 순서대로 응답 · 목록 항목에 담고, 남의 경로 · 목록 아님 · 장수 초과는 400")
    void writeWithImages() {
        List<String> issued = issue(2);
        List<String> urls = List.of(issued.get(1), issued.get(0));

        expectProblem(write(Map.of("orderItemId", "11", "rating", 5, "content", "좋아요",
            "imageUrls", List.of(issued.get(0).replace("/" + ME + "/", "/" + OTHER + "/")))),
            ReviewErrorCode.INVALID_IMAGE_URL);
        expectProblem(write(Map.of("orderItemId", "11", "rating", 5, "content", "좋아요", "imageUrls", issued.get(0))),
            ReviewErrorCode.INVALID_IMAGE_URL);
        List<String> six = new ArrayList<>(issue(5));
        six.add(issue(1).getFirst());
        expectProblem(write(Map.of("orderItemId", "11", "rating", 5, "content", "좋아요", "imageUrls", six)),
            ReviewErrorCode.TOO_MANY_IMAGES);

        write(Map.of("orderItemId", "11", "rating", 5, "content", "좋아요", "imageUrls", urls))
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.imageUrls").isEqualTo(urls);

        client.get().uri(REVIEWS + "?productId=14").exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items[0].imageUrls").isEqualTo(urls);
        client.get().uri(REVIEWS + "/me").headers(TestMembers.buyer(ME)).exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items[0].imageUrls").isEqualTo(urls);
    }

    @Test
    @DisplayName("사진 없이 쓰면 imageUrls는 빈 목록이다(작성 응답 · 목록)")
    void writeWithoutImages() {
        write(Map.of("orderItemId", "11", "rating", 5, "content", "좋아요"))
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.imageUrls").isEqualTo(List.of());
        client.get().uri(REVIEWS + "?productId=14").exchange()
            .expectBody()
            .jsonPath("$.items[0].imageUrls").isEqualTo(List.of());
    }

    @Test
    @DisplayName("PATCH: imageUrls를 보내면 교체, 빈 목록이면 모두 삭제, 키가 없으면 그대로, null이면 400 INVALID_IMAGE_URL")
    void patchImages() {
        List<String> issued = issue(3);
        String reviewId = write(Map.of("orderItemId", "11", "rating", 5, "content", "좋아요",
            "imageUrls", List.of(issued.get(0), issued.get(1))))
            .expectStatus().isCreated()
            .returnResult(Map.class)
            .getResponseBody().get("reviewId").toString();

        patch(reviewId, Map.of("imageUrls", List.of(issued.get(2), issued.get(0))))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.imageUrls").isEqualTo(List.of(issued.get(2), issued.get(0)));
        patch(reviewId, Map.of("rating", 3))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.imageUrls").isEqualTo(List.of(issued.get(2), issued.get(0)));
        Map<String, Object> nullImages = new HashMap<>();
        nullImages.put("imageUrls", null);
        expectProblem(patch(reviewId, nullImages), ReviewErrorCode.INVALID_IMAGE_URL);
        patch(reviewId, Map.of("imageUrls", List.of()))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.imageUrls").isEqualTo(List.of());
        assertThat(jdbcTemplate.queryForObject("select count(*) from community.review_image", Long.class)).isZero();
    }

    private List<String> issue(int count) {
        List<Map<String, Object>> files = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            files.add(file("image/png", 1));
        }
        Map<?, ?> body = upload(Map.of("files", files)).expectStatus().isOk()
            .returnResult(Map.class).getResponseBody();
        return ((List<?>) body.get("files")).stream().map(file -> (String) ((Map<?, ?>) file).get("imageUrl"))
            .toList();
    }

    private RestTestClient.ResponseSpec upload(Map<String, ?> body) {
        return client.post().uri(UPLOAD_URLS).headers(TestMembers.buyer(ME))
            .contentType(MediaType.APPLICATION_JSON).body(body).exchange();
    }

    private RestTestClient.ResponseSpec write(Map<String, ?> body) {
        return client.post().uri(REVIEWS).headers(TestMembers.buyer(ME))
            .contentType(MediaType.APPLICATION_JSON).body(body).exchange();
    }

    private RestTestClient.ResponseSpec patch(String reviewId, Map<String, ?> body) {
        return client.patch().uri(REVIEWS + "/" + reviewId).headers(TestMembers.buyer(ME))
            .contentType(MediaType.APPLICATION_JSON).body(body).exchange();
    }

    private static Map<String, Object> file(String contentType, long contentLength) {
        return Map.of("contentType", contentType, "contentLength", contentLength);
    }

    private static void expectProblem(RestTestClient.ResponseSpec response, ReviewErrorCode code) {
        response.expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(400)
            .jsonPath("$.title").isEqualTo(code.title());
    }
}
