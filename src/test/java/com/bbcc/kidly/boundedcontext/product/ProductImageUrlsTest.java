package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductImageUrls;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("notOwnImages")
    @DisplayName("썸네일이 판매자 본인 경로에 서버가 만든 이름으로 올린 이미지가 아니면 거절한다")
    void rejectNotOwnThumbnail(String thumbnailUrl) {
        assertThatThrownBy(() -> ProductImageUrls.validate(thumbnailUrl, List.of(), OWN_PREFIX))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.IMAGE_URL_NOT_ALLOWED));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notOwnImages")
    @DisplayName("이미지 목록에 판매자 본인 경로에 서버가 만든 이름으로 올린 이미지가 아닌 것이 하나라도 있으면 거절한다")
    void rejectNotOwnImageInList(String imageUrl) {
        assertThatThrownBy(() -> ProductImageUrls.validate(OWN_JPG, List.of(OWN_PNG, imageUrl), OWN_PREFIX))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.IMAGE_URL_NOT_ALLOWED));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingArguments")
    @DisplayName("이미지 목록이나 본인 경로가 없거나 목록에 빈 칸이 있으면 검사하지 않고 실패한다")
    void rejectMissingArgument(String thumbnailUrl, List<String> imageUrls, String ownPrefix) {
        assertThatThrownBy(() -> ProductImageUrls.validate(thumbnailUrl, imageUrls, ownPrefix))
            .isInstanceOf(NullPointerException.class);
    }

    static Stream<Arguments> missingArguments() {
        return Stream.of(
            arguments(named("본인 경로 없음", null), List.of(), null),
            arguments(named("이미지 목록 없음", OWN_JPG), null, OWN_PREFIX),
            arguments(named("목록에 빈 칸", null), Arrays.asList(OWN_PNG, null), OWN_PREFIX));
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"https://team04-kidly-s3.s3.ap-northeast-2.amazonaws.com/dev/products/2", ""})
    @DisplayName("본인 경로가 /로 끝나지 않으면 다른 판매자 경로와 구분할 수 없어 검사하지 않고 실패한다")
    void rejectOwnPrefixWithoutTrailingSlash(String ownPrefix) {
        assertThatThrownBy(() -> ProductImageUrls.validate(null, List.of(), ownPrefix))
            .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> notOwnImages() {
        String id = "3f9a1c2e-5b7d-4e60-8a91-0c2d4e6f8a10";
        String bucket = "https://team04-kidly-s3.s3.ap-northeast-2.amazonaws.com";
        return Stream.of(
            arguments(named("다른 판매자 23 (본인 2와 앞자리가 같음)", bucket + "/dev/products/23/" + id + ".jpg")),
            arguments(named("다른 환경 prod", bucket + "/prod/products/2/" + id + ".jpg")),
            arguments(named("다른 호스트", "https://images.example.com/dev/products/2/" + id + ".jpg")),
            arguments(named("http", "http://team04-kidly-s3.s3.ap-northeast-2.amazonaws.com/dev/products/2/" + id + ".jpg")),
            arguments(named("상위 경로로 이동", OWN_PREFIX + "../23/" + id + ".jpg")),
            arguments(named("하위 폴더", OWN_PREFIX + "a/" + id + ".jpg")),
            arguments(named("쿼리 붙음", OWN_JPG + "?v=1")),
            arguments(named("UUID가 아닌 이름", OWN_PREFIX + "photo.jpg")),
            arguments(named("대문자 UUID", OWN_PREFIX + id.toUpperCase() + ".jpg")),
            arguments(named("받지 않는 확장자 gif", OWN_PREFIX + id + ".gif")),
            arguments(named("대문자 확장자", OWN_PREFIX + id + ".JPG")),
            arguments(named("본인 경로만", OWN_PREFIX)),
            arguments(named("빈 문자열", "")));
    }

    static Stream<Arguments> ownImages() {
        return Stream.of(
            arguments(named("jpg 썸네일 · png와 webp 목록", OWN_JPG), List.of(OWN_PNG, OWN_WEBP)),
            arguments(named("썸네일 없음 · 빈 목록", null), List.of()));
    }
}
