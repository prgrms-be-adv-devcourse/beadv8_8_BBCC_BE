package com.bbcc.kidly.boundedcontext.recommend.domain.preference;

import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;

/**
 * 본 · 담은 상품의 임베딩을 무게로 합쳐 길이 1로 맞춘 취향 벡터다.
 * 무게의 합과 합친 활동 수는 다음 계산이 이어 받을 수 있게 함께 둔다.
 */
public record TasteVector(EmbeddingVector embedding, double weightSum, int activityCount) {
}
