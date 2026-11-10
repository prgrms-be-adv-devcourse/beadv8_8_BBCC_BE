package com.bbcc.kidly.boundedcontext.recommend;

import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;

/**
 * 선호 계산 테스트가 쓰는 1,536차원 벡터다. 몇 개 축에만 값을 두어 기대값을 손으로 계산할 수 있게 한다.
 */
public final class EmbeddingVectorFixture {

    private EmbeddingVectorFixture() {
    }

    /**
     * index 축 방향의 길이 1 벡터다.
     */
    public static EmbeddingVector axis(int index) {
        return axes(index, 1, index, 0);
    }

    /**
     * first 축에 firstValue, second 축에 secondValue를 둔 벡터다.
     */
    public static EmbeddingVector axes(int first, double firstValue, int second, double secondValue) {
        float[] values = new float[EmbeddingVector.DIMENSIONS];
        values[first] += (float) firstValue;
        values[second] += (float) secondValue;
        return new EmbeddingVector(values);
    }
}
