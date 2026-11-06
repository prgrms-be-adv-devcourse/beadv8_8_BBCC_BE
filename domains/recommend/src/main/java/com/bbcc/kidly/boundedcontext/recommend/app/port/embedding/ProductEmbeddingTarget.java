package com.bbcc.kidly.boundedcontext.recommend.app.port.embedding;

/**
 * 벡터를 새로 만들어야 하는 상품과 그 상품의 임베딩할 글이다.
 *
 * @param retried 앞서 임베딩 API에 보냈지만 성공하지 못한 상품이면 true
 */
public record ProductEmbeddingTarget(Long productId, String text, boolean retried) {
}
