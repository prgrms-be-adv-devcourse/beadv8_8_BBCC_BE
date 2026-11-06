package com.bbcc.kidly.boundedcontext.review.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 리뷰에 붙이는 사진 주소 검사(보안 경계): 우리 버킷 공개 주소 + 본인 경로 + 서버가 만든 이름만 받는다.
 */
class ReviewImageUrlsTest {

    private static final String HOST = "https://team04-kidly-s3.s3.ap-northeast-2.amazonaws.com/";
    private static final String OWN = HOST + "test/reviews/13/";
    private static final String NAME = "0b6f3c1e-8a2d-4c5b-9e7f-1a2b3c4d5e6f";
    private static final int MAX = 5;

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"jpg", "png", "webp"})
    @DisplayName("본인 경로 아래 서버가 만든 이름(UUID.확장자)은 받는다")
    void acceptsOwnGeneratedName(String extension) {
        String url = OWN + NAME + "." + extension;

        assertThat(ReviewImageUrls.of(List.of(url), OWN, MAX).urls()).containsExactly(url);
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {
        // 남의 회원 경로 · 회원 ID 앞부분만 같은 경로
        HOST + "test/reviews/14/" + NAME + ".jpg",
        HOST + "test/reviews/1/" + NAME + ".jpg",
        HOST + "test/reviews/133/" + NAME + ".jpg",
        // 다른 환경 · 다른 용도 경로
        HOST + "dev/reviews/13/" + NAME + ".jpg",
        HOST + "prod/reviews/13/" + NAME + ".jpg",
        HOST + "test/products/13/" + NAME + ".jpg",
        // 다른 호스트 · http · 대소문자 다른 호스트 · 사용자 정보 · 포트
        "https://evil.example.com/test/reviews/13/" + NAME + ".jpg",
        "https://team04-kidly-s3.s3.ap-northeast-2.amazonaws.com.evil.example/test/reviews/13/" + NAME + ".jpg",
        "http://team04-kidly-s3.s3.ap-northeast-2.amazonaws.com/test/reviews/13/" + NAME + ".jpg",
        "https://TEAM04-kidly-s3.s3.ap-northeast-2.amazonaws.com/test/reviews/13/" + NAME + ".jpg",
        "https://user@team04-kidly-s3.s3.ap-northeast-2.amazonaws.com/test/reviews/13/" + NAME + ".jpg",
        "https://team04-kidly-s3.s3.ap-northeast-2.amazonaws.com:443/test/reviews/13/" + NAME + ".jpg",
        // 쿼리 · 조각 붙음
        OWN + NAME + ".jpg?x=1",
        OWN + NAME + ".jpg#x",
        // 이름 형식 틀림: UUID 아님 · 대문자 UUID · 확장자 없음 · 이름 앞뒤에 글자
        OWN + "photo.jpg",
        OWN + "0B6F3C1E-8A2D-4C5B-9E7F-1A2B3C4D5E6F.jpg",
        OWN + NAME,
        OWN + "x" + NAME + ".jpg",
        OWN + NAME + ".jpg.png",
        // 확장자 틀림
        OWN + NAME + ".gif",
        OWN + NAME + ".jpeg",
        OWN + NAME + ".JPG",
        // 경로 조작: 상위 · 하위 경로, 인코딩, 겹친 슬래시, 역슬래시, 공백
        OWN + "../14/" + NAME + ".jpg",
        HOST + "test/reviews/14/../13/" + NAME + ".jpg",
        OWN + "%2e%2e/" + NAME + ".jpg",
        OWN + "%2F" + NAME + ".jpg",
        OWN + "sub/" + NAME + ".jpg",
        OWN + "/" + NAME + ".jpg",
        OWN + "\\" + NAME + ".jpg",
        OWN + NAME + "%2Ejpg",
        " " + OWN + NAME + ".jpg",
        OWN + NAME + ".jpg ",
        ""
    })
    @DisplayName("본인 경로 · 서버가 만든 이름이 아니면 IMAGE_URL")
    void rejectsOtherUrls(String url) {
        assertRule(() -> ReviewImageUrls.of(List.of(url), OWN, MAX), ReviewRule.IMAGE_URL);
    }

    @Test
    @DisplayName("같은 주소가 두 번 있거나 주소가 null이면 IMAGE_URL")
    void rejectsDuplicateAndNull() {
        String url = OWN + NAME + ".jpg";

        assertRule(() -> ReviewImageUrls.of(List.of(url, url), OWN, MAX), ReviewRule.IMAGE_URL);
        assertRule(() -> ReviewImageUrls.of(Collections.singletonList(null), OWN, MAX), ReviewRule.IMAGE_URL);
        assertRule(() -> ReviewImageUrls.of(null, OWN, MAX), ReviewRule.IMAGE_URL);
    }

    @Test
    @DisplayName("0장 · 최대 장수까지 받고, 최대+1장은 TOO_MANY_IMAGES(주소 검사보다 먼저)")
    void countBoundaries() {
        assertThat(ReviewImageUrls.of(List.of(), OWN, MAX).urls()).isEmpty();
        assertThat(ReviewImageUrls.of(urls(MAX), OWN, MAX).urls()).hasSize(MAX);
        assertRule(() -> ReviewImageUrls.of(urls(MAX + 1), OWN, MAX), ReviewRule.TOO_MANY_IMAGES);
        List<String> badAndTooMany = new ArrayList<>(urls(MAX));
        badAndTooMany.add("https://evil.example.com/x.jpg");
        assertRule(() -> ReviewImageUrls.of(badAndTooMany, OWN, MAX), ReviewRule.TOO_MANY_IMAGES);
    }

    @Test
    @DisplayName("사진 행은 보낸 순서대로 0부터 순서를 매긴다")
    void imagesKeepOrder() {
        List<String> urls = urls(3);

        List<ReviewImage> images = ReviewImageUrls.of(urls, OWN, MAX).toImages(7L);

        assertThat(images).extracting(ReviewImage::getReviewId, ReviewImage::getPosition, ReviewImage::getImageUrl)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(7L, 0, urls.get(0)),
                org.assertj.core.groups.Tuple.tuple(7L, 1, urls.get(1)),
                org.assertj.core.groups.Tuple.tuple(7L, 2, urls.get(2)));
    }

    private static List<String> urls(int count) {
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            urls.add(OWN + String.format("0b6f3c1e-8a2d-4c5b-9e7f-%012d", i) + ".png");
        }
        return urls;
    }

    private static void assertRule(Runnable call, ReviewRule rule) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ReviewRuleException.class,
            e -> assertThat(e.rule()).isEqualTo(rule));
    }
}
