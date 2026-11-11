package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import com.bbcc.kidly.boundedcontext.recommend.out.ai.OpenAiProductEmbedder;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

@ExtendWith(MockitoExtension.class)
class OpenAiProductEmbedderTest {

    private static final String TEXT_1 = "상품명: 데일리 맨투맨";
    private static final String TEXT_2 = "상품명: 데일리 후드티";

    @Mock
    private EmbeddingModel embeddingModel;

    private OpenAiProductEmbedder embedder;

    @BeforeEach
    void setUp() {
        embedder = new OpenAiProductEmbedder(embeddingModel);
    }

    @Test
    @DisplayName("모델이 돌려준 배열을 보낸 글과 같은 순서의 벡터로 돌려준다")
    void returnVectorsInSameOrderAsTexts() {
        float[] values1 = valuesOf(0.1f);
        float[] values2 = valuesOf(0.2f);
        given(embeddingModel.embed(List.of(TEXT_1, TEXT_2))).willReturn(List.of(values1, values2));

        List<EmbeddingVector> vectors = embedder.embed(List.of(TEXT_1, TEXT_2));

        assertThat(vectors).containsExactly(new EmbeddingVector(values1), new EmbeddingVector(values2));
    }

    @Test
    @DisplayName("모델이 돌려준 배열 개수가 보낸 글 수와 다르면 벡터를 글에 잘못 맞추지 않도록 IllegalStateException을 던진다")
    void throwWhenCountDiffersFromTexts() {
        given(embeddingModel.embed(List.of(TEXT_1, TEXT_2))).willReturn(List.of(valuesOf(0.1f)));

        assertThatThrownBy(() -> embedder.embed(List.of(TEXT_1, TEXT_2)))
            .isInstanceOf(IllegalStateException.class);
    }

    private static float[] valuesOf(float value) {
        float[] values = new float[EmbeddingVector.DIMENSIONS];
        Arrays.fill(values, value);
        return values;
    }
}
