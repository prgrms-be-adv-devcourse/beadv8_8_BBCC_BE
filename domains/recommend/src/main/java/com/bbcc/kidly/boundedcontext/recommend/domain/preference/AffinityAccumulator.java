package com.bbcc.kidly.boundedcontext.recommend.domain.preference;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 키마다 무게를 모아 친밀도 1 − Π(1 − 무게)를 만든다. 무게를 그냥 더하면 1을 넘어 다른 신호를 덮기 때문이다.
 */
final class AffinityAccumulator<K> {

    private final Map<K, Double> remainders = new LinkedHashMap<>();

    void add(K key, double weight) {
        remainders.merge(key, 1 - weight, (left, right) -> left * right);
    }

    /**
     * 키마다의 친밀도다. 처음 나온 순서를 지킨다.
     */
    Map<K, Double> affinities() {
        Map<K, Double> affinities = new LinkedHashMap<>();
        for (Map.Entry<K, Double> entry : remainders.entrySet()) {
            affinities.put(entry.getKey(), 1 - entry.getValue());
        }
        return affinities;
    }
}
