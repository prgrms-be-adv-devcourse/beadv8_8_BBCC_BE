package com.bbcc.kidly.boundedcontext.search.domain;

import static java.util.Objects.requireNonNull;

import java.util.List;

/**
 * 상품 문장(상품명 · 브랜드 · 설명)을 임베딩 API로 바꾼 벡터와, 그 문장의 해시다.
 * 다음 이벤트의 문장 해시가 같으면 API를 다시 부르지 않고 이 벡터를 그대로 쓴다.
 *
 * @param textHash 벡터를 만든 문장의 SHA-256(16진수)
 * @param vector 임베딩 벡터. 길이는 설정한 차원 수다
 */
public record ProductEmbedding(String textHash, List<Float> vector) {

    public ProductEmbedding {
        requireNonNull(textHash, "textHash는 필수입니다");
        requireNonNull(vector, "vector는 필수입니다");
        if (vector.isEmpty()) {
            throw new IllegalArgumentException("vector가 비어 있습니다");
        }
        vector = List.copyOf(vector);
    }
}
