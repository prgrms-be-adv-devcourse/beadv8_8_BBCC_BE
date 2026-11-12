package com.bbcc.kidly.boundedcontext.chatbot.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 근거 조각을 코사인 유사도로 찾는 메모리 색인. 만들 때 벡터를 단위 길이로 맞춰 두고 검색은 내적만 한다.
 * 조각 수가 문서 수 × 수십 개 수준이라 전부 훑는다.
 */
public final class KnowledgeIndex {

    private final List<Entry> entries;
    private final List<float[]> unitVectors;

    private KnowledgeIndex(List<Entry> entries, List<float[]> unitVectors) {
        this.entries = entries;
        this.unitVectors = unitVectors;
    }

    // 길이가 0인 벡터는 어떤 질문과도 비교할 수 없어 뺀다
    public static KnowledgeIndex of(List<Entry> entries) {
        List<Entry> kept = new ArrayList<>();
        List<float[]> vectors = new ArrayList<>();
        for (Entry entry : entries) {
            float[] unit = normalize(entry.embedding());
            if (unit != null) {
                kept.add(entry);
                vectors.add(unit);
            }
        }
        return new KnowledgeIndex(List.copyOf(kept), List.copyOf(vectors));
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    /**
     * 질문 벡터와 가까운 조각을 점수가 높은 순으로 최대 topK개 준다. minScore보다 낮은 조각은 넣지 않는다.
     * 임베딩 모델을 바꿔 차원이 다른 조각은 비교하지 않는다(다시 저장하면 새 모델로 임베딩된다).
     */
    public List<Match> search(float[] query, int topK, double minScore) {
        float[] unitQuery = normalize(query);
        if (unitQuery == null) {
            return List.of();
        }
        List<Match> matches = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            float[] vector = unitVectors.get(i);
            if (vector.length != unitQuery.length) {
                continue;
            }
            double score = dot(vector, unitQuery);
            if (score >= minScore) {
                matches.add(new Match(entries.get(i), score));
            }
        }
        return matches.stream()
            .sorted(Comparator.comparingDouble(Match::score).reversed())
            .limit(topK)
            .toList();
    }

    private static float[] normalize(float[] vector) {
        double norm = Math.sqrt(dot(vector, vector));
        if (norm == 0 || Double.isNaN(norm)) {
            return null;
        }
        float[] unit = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            unit[i] = (float) (vector[i] / norm);
        }
        return unit;
    }

    private static double dot(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }

    public record Entry(long chunkId, long documentId, String title, String content, float[] embedding) {
    }

    public record Match(Entry entry, double score) {
    }
}
