package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductImageUrls;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 상품 썸네일과 이미지 목록은 판매자 본인이 업로드 주소로 올린 이미지(본인 경로의 {UUID}.{확장자})만 받는다.
 */
class ProductImageUrlsTest {

    // 판매자 2가 dev 환경에서 이미지를 올리는 경로다
    private static final String OWN_PREFIX = "https://team04-kidly-s3.s3.ap-northeast-2.amazonaws.com/dev/products/2/";
    private static final String OWN_JPG = OWN_PREFIX + "3f9a1c2e-5b7d-4e60-8a91-0c2d4e6f8a10.jpg";
    private static final String OWN_PNG = OWN_PREFIX + "4a0b2d3f-6c8e-4f71-9b02-1d3e5f708b21.png";
    private static final String OWN_WEBP = OWN_PREFIX + "5b1c3e4a-7d9f-4a82-8c13-2e4f6a819c32.webp";

    @ParameterizedTest(name = "{0}")
    @MethodSource("ownImages")
    @DisplayName("썸네일과 이미지 목록이 판매자 본인 경로의 이미지이면 통과한다")
    void acceptOwnImages(String thumbnailUrl, List<String> imageUrls) {
        assertThatCode(() -> ProductImageUrls.validate(thumbnailUrl, imageUrls, OWN_PREFIX))
            .doesNotThrowAnyException();
    }

    static Stream<Arguments> ownImages() {
        return Stream.of(
            arguments(named("jpg 썸네일 · png와 webp 목록", OWN_JPG), List.of(OWN_PNG, OWN_WEBP)),
            arguments(named("썸네일 없음 · 빈 목록", null), List.of()));
    }
}
