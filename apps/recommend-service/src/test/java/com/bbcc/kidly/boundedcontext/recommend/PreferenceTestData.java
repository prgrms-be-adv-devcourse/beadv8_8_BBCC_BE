package com.bbcc.kidly.boundedcontext.recommend;

import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;

/**
 * 선호 저장 · 읽기 통합 테스트가 함께 쓰는 값을 만든다.
 */
final class PreferenceTestData {

    static EmbeddingVector vectorOf(float seed) {
        float[] values = new float[EmbeddingVector.DIMENSIONS];
        for (int i = 0; i < values.length; i++) {
            values[i] = seed + i / 1_000f;
        }
        return new EmbeddingVector(values);
    }

    private PreferenceTestData() {
    }
}
