package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.ProductEmbeddingText;
import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.ProductEmbeddingText.InfoValue;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductEmbeddingTextTest {

    @Test
    @DisplayName("상품명 · 카테고리 경로 · 브랜드 · 정보 값 · 설명을 이 순서로 한 줄씩 잇고, 경로는 ' > '로 잇는다")
    void composeLinesInOrder() {
        ProductEmbeddingText text = ProductEmbeddingText.compose("면 티셔츠", List.of("의류", "상의"), "키들리",
            List.of(new InfoValue("소재", "면")), "부드러운 면 티셔츠");

        assertThat(text.value()).isEqualTo("""
            상품명: 면 티셔츠
            카테고리: 의류 > 상의
            브랜드: 키들리
            소재: 면
            설명: 부드러운 면 티셔츠""");
    }

    @ParameterizedTest
    @MethodSource("pathsWithoutName")
    @DisplayName("카테고리 경로가 없거나 비었거나 이름이 모두 비어 있으면 카테고리 줄을 뺀다")
    void omitCategoryLineWithoutPathName(List<String> categoryPath) {
        ProductEmbeddingText text = ProductEmbeddingText.compose("면 티셔츠", categoryPath, "키들리", List.of(),
            "부드러운 면 티셔츠");

        assertThat(text.value()).isEqualTo("""
            상품명: 면 티셔츠
            브랜드: 키들리
            설명: 부드러운 면 티셔츠""");
    }

    private static Stream<Arguments> pathsWithoutName() {
        return Stream.of(
            Arguments.of(Named.of("null", null)),
            Arguments.of(Named.of("빈 목록", List.of())),
            Arguments.of(Named.of("이름이 모두 공백", Arrays.asList(null, "", "  "))));
    }

    @Test
    @DisplayName("카테고리 경로 중 이름이 없거나 공백인 칸은 빼고 나머지를 순서대로 잇는다")
    void skipBlankNamesInCategoryPath() {
        ProductEmbeddingText text = ProductEmbeddingText.compose("면 티셔츠",
            Arrays.asList("의류", null, " ", "상의"), null, List.of(), null);

        assertThat(text.value()).isEqualTo("""
            상품명: 면 티셔츠
            카테고리: 의류 > 상의""");
    }

    @Test
    @DisplayName("같은 code의 정보 값은 한 줄에 값 오름차순으로, code는 오름차순으로 쓴다")
    void groupInfoValuesByCodeInOrder() {
        ProductEmbeddingText text = ProductEmbeddingText.compose("면 티셔츠", List.of(), null, List.of(
            new InfoValue("season", "winter"),
            new InfoValue("material", "wool"),
            new InfoValue("season", "fall"),
            new InfoValue("material", "cotton")), null);

        assertThat(text.value()).isEqualTo("""
            상품명: 면 티셔츠
            material: cotton, wool
            season: fall, winter""");
    }

    @Test
    @DisplayName("정보 값의 순서만 다른 두 입력은 같은 글이 된다")
    void sameTextRegardlessOfInfoValueOrder() {
        List<InfoValue> infoValues = List.of(
            new InfoValue("material", "wool"),
            new InfoValue("season", "fall"),
            new InfoValue("material", "cotton"));
        List<InfoValue> reordered = List.of(infoValues.get(2), infoValues.get(1), infoValues.get(0));

        assertThat(ProductEmbeddingText.compose("면 티셔츠", List.of(), "키들리", reordered, "설명"))
            .isEqualTo(ProductEmbeddingText.compose("면 티셔츠", List.of(), "키들리", infoValues, "설명"));
    }

    @Test
    @DisplayName("값이 없거나 공백인 정보 값은 빼고, 값이 모두 빠진 code는 줄을 두지 않는다")
    void omitBlankInfoValues() {
        ProductEmbeddingText text = ProductEmbeddingText.compose("면 티셔츠", List.of(), null, List.of(
            new InfoValue("material", "cotton"),
            new InfoValue("material", null),
            new InfoValue("material", " "),
            new InfoValue("season", ""),
            new InfoValue("season", null)), null);

        assertThat(text.value()).isEqualTo("""
            상품명: 면 티셔츠
            material: cotton""");
    }

    @Test
    @DisplayName("글이 4,000자를 넘으면 4,000 code point에서 자르고 마지막 줄인 설명이 잘린다")
    void truncateDescriptionAtMaxLength() {
        ProductEmbeddingText text = ProductEmbeddingText.compose("면 티셔츠", List.of(), "키들리", List.of(),
            "a".repeat(5_000));

        assertThat(text.value().codePointCount(0, text.value().length()))
            .isEqualTo(ProductEmbeddingText.MAX_LENGTH);
        assertThat(text.value()).startsWith("상품명: 면 티셔츠\n브랜드: 키들리\n설명: aaa").endsWith("a");
    }

    @Test
    @DisplayName("자르는 경계에 서로게이트 쌍이 있으면 쌍을 가르지 않는다")
    void truncateWithoutSplittingSurrogatePair() {
        String head = "상품명: 면 티셔츠\n설명: ";
        String description = "a".repeat(ProductEmbeddingText.MAX_LENGTH - 1 - head.length()) + "😀😀😀";

        ProductEmbeddingText text = ProductEmbeddingText.compose("면 티셔츠", List.of(), null, List.of(), description);

        assertThat(text.value()).endsWith("a😀");
        assertThat(text.value().codePointCount(0, text.value().length()))
            .isEqualTo(ProductEmbeddingText.MAX_LENGTH);
    }

    @Test
    @DisplayName("상품명이 없으면 글을 만들지 않는다")
    void rejectNullName() {
        assertThatThrownBy(() -> ProductEmbeddingText.compose(null, List.of(), "키들리", List.of(), "설명"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("정보 값 목록이 없으면 빈 목록으로 보고 글을 만든다")
    void treatNullInfoValuesAsEmpty() {
        ProductEmbeddingText text = ProductEmbeddingText.compose("면 티셔츠", List.of(), "키들리", null, "설명");

        assertThat(text.value()).isEqualTo("""
            상품명: 면 티셔츠
            브랜드: 키들리
            설명: 설명""");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("브랜드가 없거나 공백이면 브랜드 줄을 뺀다")
    void omitBlankBrand(String brand) {
        ProductEmbeddingText text = ProductEmbeddingText.compose("면 티셔츠", List.of(), brand, List.of(), "부드러운 면 티셔츠");

        assertThat(text.value()).isEqualTo("""
            상품명: 면 티셔츠
            설명: 부드러운 면 티셔츠""");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("설명이 없거나 공백이면 설명 줄을 뺀다")
    void omitBlankDescription(String description) {
        ProductEmbeddingText text = ProductEmbeddingText.compose("면 티셔츠", List.of(), "키들리", List.of(), description);

        assertThat(text.value()).isEqualTo("""
            상품명: 면 티셔츠
            브랜드: 키들리""");
    }
}
