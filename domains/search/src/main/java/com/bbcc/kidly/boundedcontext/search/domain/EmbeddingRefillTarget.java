package com.bbcc.kidly.boundedcontext.search.domain;

import static java.util.Objects.requireNonNull;

import java.time.LocalDateTime;

/**
 * 벡터 없이 저장된 판매중 상품 문서다. 저장해 둔 문장으로 벡터를 다시 만든다.
 *
 * @param productId 상품 ID
 * @param embeddingText 벡터 없이 저장할 때 함께 저장한 의미 검색 문장
 * @param occurredAt 그 문서를 만든 이벤트의 발생 시각. 벡터를 만드는 동안 문서가 바뀌었는지 보는 데 쓴다
 */
public record EmbeddingRefillTarget(Long productId, String embeddingText, LocalDateTime occurredAt) {

    public EmbeddingRefillTarget {
        requireNonNull(productId, "productId는 필수입니다");
        requireNonNull(embeddingText, "embeddingText는 필수입니다");
        requireNonNull(occurredAt, "occurredAt은 필수입니다");
    }

    /**
     * 문장의 SHA-256(16진수)이다. 이벤트로 만든 문서의 해시와 같은 방법으로 센다.
     */
    public String textHash() {
        return SearchProduct.hashOf(embeddingText);
    }
}
