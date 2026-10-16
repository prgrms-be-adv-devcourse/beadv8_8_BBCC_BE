package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductImageFormat;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductImageUpload;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductImageUploadTest {

    private static final UUID ID = UUID.fromString("3f9a1c2e-5b7d-4e60-8a91-0c2d4e6f8a10");

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "image/jpeg, dev/products/2/3f9a1c2e-5b7d-4e60-8a91-0c2d4e6f8a10.jpg",
        "image/png, dev/products/2/3f9a1c2e-5b7d-4e60-8a91-0c2d4e6f8a10.png",
        "image/webp, dev/products/2/3f9a1c2e-5b7d-4e60-8a91-0c2d4e6f8a10.webp"})
    @DisplayName("허용한 형식이면 환경 · 판매자 · UUID와 형식에 맞는 확장자로 객체 key를 만든다")
    void keyForAllowedType(String contentType, String expectedKey) {
        ProductImageUpload upload = ProductImageUpload.of(contentType, 1_000L);

        assertThat(upload.key("dev", SELLER_ID, ID)).isEqualTo(expectedKey);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"image/svg+xml", "image/gif", "image/heic", "text/html", "IMAGE/JPEG", ""})
    @DisplayName("jpeg · png · webp가 아닌 형식이면 업로드를 만들지 않는다")
    void rejectUnsupportedType(String contentType) {
        assertThatThrownBy(() -> ProductImageUpload.of(contentType, 1_000L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(longs = {1L, 10_485_760L})
    @DisplayName("크기가 1바이트 이상 10MB 이하면 업로드를 만든다")
    void acceptSizeInRange(long contentLength) {
        ProductImageUpload upload = ProductImageUpload.of("image/jpeg", contentLength);

        assertThat(upload.contentLength()).isEqualTo(contentLength);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(longs = {0L, -1L, 10_485_761L})
    @DisplayName("크기가 0 이하이거나 10MB를 넘으면 업로드를 만들지 않는다")
    void rejectOutOfRangeSize(long contentLength) {
        assertThatThrownBy(() -> ProductImageUpload.of("image/jpeg", contentLength))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(longs = {0L, -1L, 10_485_761L})
    @DisplayName("생성자로 직접 만들어도 크기가 0 이하이거나 10MB를 넘으면 업로드를 만들지 않는다")
    void rejectOutOfRangeSizeOnConstructor(long contentLength) {
        assertThatThrownBy(() -> new ProductImageUpload(ProductImageFormat.JPEG, contentLength))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("생성자로 직접 만들어도 형식이 없으면 업로드를 만들지 않는다")
    void rejectNullFormatOnConstructor() {
        assertThatThrownBy(() -> new ProductImageUpload(null, 1_000L))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("형식이 없으면 업로드를 만들지 않는다")
    void rejectNullContentType() {
        assertThatThrownBy(() -> ProductImageUpload.of(null, 1_000L))
            .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"", " ", "dev/", "/dev", "de/v"})
    @DisplayName("환경 값이 비었거나 /를 담으면 객체 key를 만들지 않는다")
    void rejectKeyWithInvalidEnvironment(String environment) {
        ProductImageUpload upload = ProductImageUpload.of("image/jpeg", 1_000L);

        assertThatThrownBy(() -> upload.key(environment, SELLER_ID, ID)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("환경이나 UUID가 없으면 객체 key를 만들지 않는다")
    void rejectKeyWithoutEnvironmentOrId() {
        ProductImageUpload upload = ProductImageUpload.of("image/jpeg", 1_000L);

        assertThatThrownBy(() -> upload.key(null, SELLER_ID, ID)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> upload.key("dev", SELLER_ID, null)).isInstanceOf(NullPointerException.class);
    }
}
