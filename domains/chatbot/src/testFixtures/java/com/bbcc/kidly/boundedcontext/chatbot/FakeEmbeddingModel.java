package com.bbcc.kidly.boundedcontext.chatbot;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * OpenAI 없이 쓰는 임베딩 모델. 낱말 안의 두 글자 묶음을 세어 벡터로 만들어, 같은 낱말을 많이 나누는 글일수록 가깝다.
 * {@link #failWith}로 호출 실패를 흉내 낸다.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = 512;

    private volatile RuntimeException failure;

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        RuntimeException current = failure;
        if (current != null) {
            throw current;
        }
        List<Embedding> embeddings = new ArrayList<>();
        List<String> texts = request.getInstructions();
        for (int i = 0; i < texts.size(); i++) {
            embeddings.add(new Embedding(vectorOf(texts.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vectorOf(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    public static float[] vectorOf(String text) {
        float[] vector = new float[DIMENSIONS];
        for (String word : text.split("[^\\p{L}\\p{N}]+")) {
            for (int i = 0; i + 1 < word.length(); i++) {
                vector[Math.floorMod(word.substring(i, i + 2).hashCode(), DIMENSIONS)] += 1;
            }
        }
        return vector;
    }
}
