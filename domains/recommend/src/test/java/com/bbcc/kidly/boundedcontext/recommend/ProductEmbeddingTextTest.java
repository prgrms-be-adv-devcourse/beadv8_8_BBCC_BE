package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.ProductEmbeddingText;
import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.ProductEmbeddingText.InfoValue;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductEmbeddingTextTest {

    @Test
    @DisplayName("상품명 · 브랜드 · 정보 값 · 설명을 이 순서로 한 줄씩 잇는다")
    void composeLinesInOrder() {
        ProductEmbeddingText text = ProductEmbeddingText.compose("면 티셔츠", "키들리",
            List.of(new InfoValue("소재", "면")), "부드러운 면 티셔츠");

        assertThat(text.value()).isEqualTo("""
            상품명: 면 티셔츠
            브랜드: 키들리
            소재: 면
            설명: 부드러운 면 티셔츠""");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("브랜드가 없거나 공백이면 브랜드 줄을 뺀다")
    void omitBlankBrand(String brand) {
        ProductEmbeddingText text = ProductEmbeddingText.compose("면 티셔츠", brand, List.of(), "부드러운 면 티셔츠");

        assertThat(text.value()).isEqualTo("""
            상품명: 면 티셔츠
            설명: 부드러운 면 티셔츠""");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("설명이 없거나 공백이면 설명 줄을 뺀다")
    void omitBlankDescription(String description) {
        ProductEmbeddingText text = ProductEmbeddingText.compose("면 티셔츠", "키들리", List.of(), description);

        assertThat(text.value()).isEqualTo("""
            상품명: 면 티셔츠
            브랜드: 키들리""");
    }
}
