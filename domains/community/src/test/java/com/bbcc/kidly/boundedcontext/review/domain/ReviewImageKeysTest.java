package com.bbcc.kidly.boundedcontext.review.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 지울 수 있는 key는 이 환경 리뷰 경로 · 양수 회원 ID · 서버가 만든 이름(소문자 UUID.jpg|png|webp)뿐이다.
 */
class ReviewImageKeysTest {

    private static final String NAME = "0b6f3c1e-8a2d-4c5b-9e7f-1a2b3c4d5e6f";

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {
        "dev/reviews/13/" + NAME + ".jpg",
        "dev/reviews/1/" + NAME + ".png",
        "dev/reviews/9223372036854775807/" + NAME + ".webp"})
    @DisplayName("이 환경 리뷰 경로 · 양수 회원 ID · UUID · 받는 확장자면 지울 수 있다")
    void cleanable(String key) {
        assertThat(ReviewImageKeys.isCleanable("dev", key)).isTrue();
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {
        "prod/reviews/13/" + NAME + ".jpg",
        "devx/reviews/13/" + NAME + ".jpg",
        "de/reviews/13/" + NAME + ".jpg",
        "dev/products/13/" + NAME + ".jpg",
        "dev/review/13/" + NAME + ".jpg",
        "dev/reviews/0/" + NAME + ".jpg",
        "dev/reviews/-1/" + NAME + ".jpg",
        "dev/reviews/013/" + NAME + ".jpg",
        "dev/reviews/9223372036854775808/" + NAME + ".jpg",
        "dev/reviews/abc/" + NAME + ".jpg",
        "dev/reviews/13/" + "0B6F3C1E-8A2D-4C5B-9E7F-1A2B3C4D5E6F" + ".jpg",
        "dev/reviews/13/" + NAME + ".gif",
        "dev/reviews/13/" + NAME + ".JPG",
        "dev/reviews/13/" + NAME,
        "dev/reviews/13/x/" + NAME + ".jpg",
        "dev/reviews/13/../" + NAME + ".jpg",
        "dev/reviews/../reviews/13/" + NAME + ".jpg",
        "dev/reviews/13/%2e%2e/" + NAME + ".jpg",
        "dev/reviews/13/" + NAME + ".jpg?x=1",
        "/dev/reviews/13/" + NAME + ".jpg",
        "dev/reviews/13/",
        ""})
    @DisplayName("다른 환경 · 다른 기능 경로 · 0 이하 · 숫자 아닌 회원 ID · 대문자 UUID · 다른 확장자 · 경로 조작은 지우지 않는다")
    void notCleanable(String key) {
        assertThat(ReviewImageKeys.isCleanable("dev", key)).isFalse();
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"", "x"})
    @DisplayName("환경 경로가 비었거나 key가 null이면 지우지 않는다")
    void blankPrefixOrNullKey(String prefix) {
        assertThat(ReviewImageKeys.isCleanable(prefix.isEmpty() ? null : "", "/reviews/13/" + NAME + ".jpg"))
            .isFalse();
        assertThat(ReviewImageKeys.isCleanable("dev", null)).isFalse();
    }
}
