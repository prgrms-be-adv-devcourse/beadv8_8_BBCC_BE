package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/**
 * 판매자가 상품 이미지를 S3에 직접 올릴 업로드 주소를 받는 API를 HTTP 요청부터 응답까지 확인한다.
 * 테스트 프로필은 가짜 자격 증명으로 서명하므로 AWS에 요청하지 않는다(key 경로는 test).
 */
@FunctionalTest
class SellerProductImageUploadUrlApiTest {

    private static final String PATH = "/api/v1/seller/product-images/upload-urls";
    private static final String OBJECT_URL = "https://team04-kidly-s3\\.s3\\.ap-northeast-2\\.amazonaws\\.com"
        + "/test/products/2/[0-9a-f-]{36}";

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Test
    @DisplayName("판매자가 이미지 두 장의 업로드 주소를 요청하면 요청 순서대로 본인 경로의 주소와 만료 시각을 받는다")
    void issuesUploadUrlsInRequestOrder() {
        Map<String, Object> body = Map.of("files", List.of(
            Map.of("contentType", "image/jpeg", "contentLength", 1_000),
            Map.of("contentType", "image/png", "contentLength", 2_000)));

        issue(tokens.seller(SELLER_ID), body)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.uploads.length()").isEqualTo(2)
            .jsonPath("$.uploads[0].imageUrl").value(url -> assertThat((String) url).matches(OBJECT_URL + "\\.jpg"))
            .jsonPath("$.uploads[1].imageUrl").value(url -> assertThat((String) url).matches(OBJECT_URL + "\\.png"))
            .jsonPath("$.uploads[0].uploadUrl").value(url -> assertThat((String) url)
                .matches(OBJECT_URL + "\\.jpg\\?.*").contains("X-Amz-Signature="))
            .jsonPath("$.uploads[0].expiresAt").isNotEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    @DisplayName("형식 · 크기 · 장수가 맞지 않는 요청은 400과 ProblemDetail로 거절한다")
    void rejectInvalidRequest(Map<String, Object> body, String detail) {
        issue(tokens.seller(SELLER_ID), body)
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").exists()
            .jsonPath("$.title").isEqualTo("잘못된 요청입니다")
            .jsonPath("$.status").isEqualTo(400)
            .jsonPath("$.detail").isEqualTo(detail)
            .jsonPath("$.instance").isEqualTo(PATH);
    }

    @Test
    @DisplayName("로그인하지 않으면 401, 판매자가 아닌 회원이면 403으로 거절한다")
    void rejectWithoutSellerRole() {
        Map<String, Object> body = files(file("image/jpeg", 1_000));

        issue(null, body).expectStatus().isUnauthorized();
        issue(tokens.buyer(SELLER_ID), body).expectStatus().isForbidden();
    }

    static Stream<Arguments> invalidRequests() {
        String format = "files[0].contentType: jpeg, png, webp 이미지만 올릴 수 있습니다.";
        String size = "files[0].contentLength: 이미지는 한 장에 1바이트 이상 10MB 이하로 올려 주세요.";
        String count = "files: 이미지는 한 번에 1장 이상 10장 이하로 요청해 주세요.";
        return Stream.of(
            arguments(named("SVG 형식", files(file("image/svg+xml", 1_000))), format),
            arguments(named("빈 형식", files(file("", 1_000))), format),
            arguments(named("형식 없음", files(Map.of("contentLength", 1_000))),
                "files[0].contentType: 이미지 형식을 넣어 주세요."),
            arguments(named("크기 0", files(file("image/jpeg", 0))), size),
            arguments(named("크기 10MB + 1", files(file("image/jpeg", 10_485_761))), size),
            arguments(named("빈 목록", files()), count),
            arguments(named("11장", Map.of("files", Collections.nCopies(11, file("image/jpeg", 1_000)))), count),
            arguments(named("목록 없음", Map.of()), "files: 이미지 목록을 넣어 주세요."));
    }

    @SafeVarargs
    private static Map<String, Object> files(Map<String, Object>... files) {
        return Map.of("files", List.of(files));
    }

    private static Map<String, Object> file(String contentType, long contentLength) {
        return Map.of("contentType", contentType, "contentLength", contentLength);
    }

    private ResponseSpec issue(String token, Map<String, Object> body) {
        return client.post().uri(PATH)
            .headers(headers -> {
                if (token != null) {
                    headers.setBearerAuth(token);
                }
            })
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange();
    }
}
