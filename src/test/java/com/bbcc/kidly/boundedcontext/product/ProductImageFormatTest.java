package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductImageFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductImageFormatTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(ProductImageFormat.class)
    @DisplayName("받을 수 있는 형식의 Content-Type은 요청 검증에 쓰는 형식 정규식에 맞는다")
    void contentTypeMatchesPattern(ProductImageFormat format) {
        assertThat(format.contentType()).matches(ProductImageFormat.CONTENT_TYPE_PATTERN);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"image/svg+xml", "image/gif", "image/jpg", "IMAGE/JPEG", "image/jpeg; charset=utf-8"})
    @DisplayName("받지 않는 형식은 요청 검증에 쓰는 형식 정규식에 맞지 않는다")
    void otherContentTypeDoesNotMatchPattern(String contentType) {
        assertThat(contentType).doesNotMatch(ProductImageFormat.CONTENT_TYPE_PATTERN);
    }
}
