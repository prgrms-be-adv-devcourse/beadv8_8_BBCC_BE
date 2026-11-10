package com.bbcc.kidly.boundedcontext.recommend.out.preference;

import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import java.util.Arrays;

/**
 * pgvector가 받고 돌려주는 글(`[1.0,2.0]`)과 벡터를 서로 바꾼다.
 */
final class VectorText {

    static String format(EmbeddingVector vector) {
        return Arrays.toString(vector.values());
    }

    static EmbeddingVector parse(String text) {
        String[] tokens = text.substring(1, text.length() - 1).split(",");
        float[] values = new float[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            values[i] = Float.parseFloat(tokens[i].trim());
        }
        return new EmbeddingVector(values);
    }

    private VectorText() {
    }
}
