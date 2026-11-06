package com.bbcc.kidly.boundedcontext.review.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ReviewImageUploadTest {

    private static final long MAX_BYTES = 10_485_760L;

    @ParameterizedTest(name = "[{0}] → .{1}")
    @CsvSource({"image/jpeg, jpg", "image/png, png", "image/webp, webp"})
    @DisplayName("jpg · png · webp는 받고 형식마다 확장자가 정해진다")
    void acceptedFormats(String contentType, String extension) {
        ReviewImageUpload upload = ReviewImageUpload.of(contentType, 1L, MAX_BYTES);

        assertThat(upload.format().contentType()).isEqualTo(contentType);
        assertThat(upload.format().extension()).isEqualTo(extension);
    }

    @ParameterizedTest(name = "[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"image/gif", "image/jpg", "IMAGE/JPEG", "image/jpeg; charset=utf-8", " image/png",
        "application/octet-stream", "image/svg+xml"})
    @DisplayName("그 밖의 형식(대소문자 · 공백 · 매개변수 붙음 포함)은 IMAGE_FILE")
    void rejectsOtherFormats(String contentType) {
        assertRule(() -> ReviewImageUpload.of(contentType, 1L, MAX_BYTES), ReviewRule.IMAGE_FILE);
    }

    @Test
    @DisplayName("크기는 1바이트 ~ 최대 크기(10MB)까지: 0 · 최대+1 · 없음은 IMAGE_FILE")
    void sizeBoundaries() {
        assertThat(ReviewImageUpload.of("image/png", 1L, MAX_BYTES).contentLength()).isEqualTo(1L);
        assertThat(ReviewImageUpload.of("image/png", MAX_BYTES, MAX_BYTES).contentLength()).isEqualTo(MAX_BYTES);
        assertRule(() -> ReviewImageUpload.of("image/png", 0L, MAX_BYTES), ReviewRule.IMAGE_FILE);
        assertRule(() -> ReviewImageUpload.of("image/png", -1L, MAX_BYTES), ReviewRule.IMAGE_FILE);
        assertRule(() -> ReviewImageUpload.of("image/png", MAX_BYTES + 1, MAX_BYTES), ReviewRule.IMAGE_FILE);
        assertRule(() -> ReviewImageUpload.of("image/png", null, MAX_BYTES), ReviewRule.IMAGE_FILE);
    }

    @Test
    @DisplayName("객체 key는 {환경}/reviews/{회원 ID}/{UUID}.{확장자}이고, 서버가 만든 이름으로 판별된다")
    void keyShape() {
        UUID name = UUID.fromString("0b6f3c1e-8a2d-4c5b-9e7f-1a2b3c4d5e6f");

        String key = ReviewImageUpload.of("image/webp", 10L, MAX_BYTES).key("dev", 13L, name);

        assertThat(key).isEqualTo("dev/reviews/13/0b6f3c1e-8a2d-4c5b-9e7f-1a2b3c4d5e6f.webp");
        assertThat(ReviewImageUpload.directory("dev", 13L)).isEqualTo("dev/reviews/13/");
        assertThat(key).startsWith(ReviewImageUpload.directory("dev", 13L));
        assertThat(ReviewImageUpload.isGeneratedName(key.substring("dev/reviews/13/".length()))).isTrue();
    }

    private static void assertRule(Runnable call, ReviewRule rule) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ReviewRuleException.class,
            e -> assertThat(e.rule()).isEqualTo(rule));
    }
}
