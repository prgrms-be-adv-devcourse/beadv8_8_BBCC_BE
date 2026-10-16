package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.domain.image.ProductImageUpload;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
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
    @ValueSource(longs = {0L, -1L, 10_485_761L})
    @DisplayName("크기가 0 이하이거나 10MB를 넘으면 업로드를 만들지 않는다")
    void rejectOutOfRangeSize(long contentLength) {
        assertThatThrownBy(() -> ProductImageUpload.of("image/jpeg", contentLength))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
