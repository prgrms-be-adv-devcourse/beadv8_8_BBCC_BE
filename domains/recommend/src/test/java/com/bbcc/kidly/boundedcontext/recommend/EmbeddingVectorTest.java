package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmbeddingVectorTest {

    @ParameterizedTest(name = "{0}차원")
    @ValueSource(ints = {0, 512, 1535, 1537})
    @DisplayName("차원이 1,536이 아니면 벡터를 만들지 않는다")
    void rejectWrongDimensions(int dimensions) {
        assertThatThrownBy(() -> new EmbeddingVector(new float[dimensions]))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(floats = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    @DisplayName("값 중 한 칸이라도 NaN이나 무한대면 벡터를 만들지 않는다")
    void rejectNonFiniteValue(float nonFinite) {
        float[] values = new float[EmbeddingVector.DIMENSIONS];
        values[EmbeddingVector.DIMENSIONS - 1] = nonFinite;

        assertThatThrownBy(() -> new EmbeddingVector(values))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("만든 뒤에 넘긴 배열이나 돌려받은 배열을 바꿔도 벡터는 바뀌지 않는다")
    void keepValuesWhenArraysAreChanged() {
        float[] given = new float[EmbeddingVector.DIMENSIONS];
        given[0] = 0.5f;
        EmbeddingVector vector = new EmbeddingVector(given);

        given[0] = 9f;
        vector.values()[0] = 7f;

        assertThat(vector.values()[0]).isEqualTo(0.5f);
    }
}
