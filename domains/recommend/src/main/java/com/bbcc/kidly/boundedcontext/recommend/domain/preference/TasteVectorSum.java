package com.bbcc.kidly.boundedcontext.recommend.domain.preference;

import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;

/**
 * 벡터를 무게만큼 더해 가다가 마지막에 길이 1로 맞춰 취향 벡터를 만든다.
 */
final class TasteVectorSum {

    private final double[] sum = new double[EmbeddingVector.DIMENSIONS];

    private double weightSum;

    private int activityCount;

    void add(EmbeddingVector vector, double weight, int activities) {
        float[] values = vector.values();
        for (int i = 0; i < values.length; i++) {
            sum[i] += weight * values[i];
        }
        weightSum += weight;
        activityCount += activities;
    }

    /**
     * 더한 벡터가 없거나 합이 0 벡터면 방향이 없으므로 null을 돌려준다.
     */
    TasteVector toTasteVector() {
        if (activityCount == 0) {
            return null;
        }
        double squaredLength = 0;
        for (double value : sum) {
            squaredLength += value * value;
        }
        if (squaredLength == 0) {
            return null;
        }
        double length = Math.sqrt(squaredLength);
        float[] normalized = new float[sum.length];
        for (int i = 0; i < sum.length; i++) {
            normalized[i] = (float) (sum[i] / length);
        }
        return new TasteVector(new EmbeddingVector(normalized), weightSum, activityCount);
    }
}
