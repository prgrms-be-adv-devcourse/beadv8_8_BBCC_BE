package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import org.junit.jupiter.api.DisplayName;
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
}
