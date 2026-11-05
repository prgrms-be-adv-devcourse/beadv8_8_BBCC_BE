package com.bbcc.kidly.boundedcontext.search.out;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 의미 검색 임베딩 설정(#797). 키가 비어 있으면 의미 검색을 끈다(로컬 · 테스트). 그 밖의 값이 비었거나 범위 밖이면 기동에 실패한다.
 *
 * @param apiKey OpenAI API 키. 비어 있으면 임베딩 API를 부르지 않는다
 * @param baseUrl 임베딩 API 주소(/embeddings 앞까지)
 * @param model 임베딩 모델
 * @param dimensions 벡터 차원 수. 상품 색인 설정(elasticsearch/products.json)의 embedding dims와 같아야 한다.
 *     바꾸면 색인을 새로 만들고 다시 채워야 한다
 * @param minSimilarity 의미 검색으로 찾을 최소 코사인 유사도. 이보다 먼 상품은 검색어와 관계없다고 보고 넣지 않는다
 */
@Validated
@ConfigurationProperties("kidly.search.embedding")
public record SearchEmbeddingProperties(
    String apiKey,
    @NotBlank String baseUrl,
    @NotBlank String model,
    @Positive int dimensions,
    @DecimalMin("0.0") @DecimalMax("1.0") float minSimilarity) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    // 키가 로그 · 오류 메시지에 찍히지 않게 한다
    @Override
    public String toString() {
        return "SearchEmbeddingProperties[apiKey=" + (enabled() ? "***" : "") + ", baseUrl=" + baseUrl
            + ", model=" + model + ", dimensions=" + dimensions + ", minSimilarity=" + minSimilarity + "]";
    }
}
