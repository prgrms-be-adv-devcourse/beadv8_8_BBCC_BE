package com.bbcc.kidly.boundedcontext.recommend.domain.embedding;

import static java.util.Objects.requireNonNull;

import java.util.Arrays;

/**
 * 임베딩 API가 돌려준 상품 글의 벡터다. 저장 칸의 차원과 같은 길이만 만들 수 있다.
 */
public record EmbeddingVector(float[] values) {

    public static final int DIMENSIONS = 1_536;

    public EmbeddingVector {
        requireNonNull(values, "values는 필수입니다");
        if (values.length != DIMENSIONS) {
            throw new IllegalArgumentException("벡터의 차원은 " + DIMENSIONS + "이어야 합니다: " + values.length);
        }
        for (float value : values) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("벡터의 값은 NaN이나 무한대일 수 없습니다");
            }
        }
        values = values.clone();
    }

    @Override
    public float[] values() {
        return values.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EmbeddingVector vector && Arrays.equals(values, vector.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "EmbeddingVector[" + values.length + "차원]";
    }
}
