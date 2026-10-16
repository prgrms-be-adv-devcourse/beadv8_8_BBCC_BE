package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
