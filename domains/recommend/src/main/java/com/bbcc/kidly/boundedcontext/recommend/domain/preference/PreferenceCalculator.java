package com.bbcc.kidly.boundedcontext.recommend.domain.preference;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 자녀 또는 회원 한 명의 신호로 카테고리 · 브랜드 친밀도와 취향 벡터를 계산한다.
 */
public class PreferenceCalculator {

    private static final double SECONDS_PER_DAY = 86_400.0;

    public PreferenceSnapshot calculate(
            List<PreferenceSignal> signals,
            Map<Long, EmbeddingVector> productEmbeddings,
            LocalDateTime baseTime
    ) {
        requireNonNull(signals, "signals는 필수입니다");
        requireNonNull(productEmbeddings, "productEmbeddings는 필수입니다");
        requireNonNull(baseTime, "baseTime은 필수입니다");
        // 친밀도는 1 − Π(1 − 무게)이므로 키마다 Π(1 − 무게)를 모은다
        Map<Long, Double> categoryRemainders = new LinkedHashMap<>();
        Map<String, Double> brandRemainders = new LinkedHashMap<>();
        TasteVectorSum tasteVectorSum = new TasteVectorSum();
        for (PreferenceSignal signal : signals) {
            double weight = weightOf(signal, baseTime);
            if (weight <= 0) {
                continue;
            }
            if (signal.type().countsForCategory() && signal.categoryId() != null) {
                categoryRemainders.merge(signal.categoryId(), 1 - weight, (left, right) -> left * right);
            }
            if (signal.brand() != null && !signal.brand().isBlank()) {
                brandRemainders.merge(signal.brand(), 1 - weight, (left, right) -> left * right);
            }
            if (!signal.type().countsForTaste() || signal.productId() == null) {
                continue;
            }
            // 벡터가 아직 없는 상품을 0 벡터로 넣으면 활동이 많을수록 유사도가 낮아진다
            EmbeddingVector productEmbedding = productEmbeddings.get(signal.productId());
            if (productEmbedding != null) {
                tasteVectorSum.add(productEmbedding, weight, 1);
            }
        }
        List<CategoryAffinity> categories = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : categoryRemainders.entrySet()) {
            categories.add(new CategoryAffinity(entry.getKey(), 1 - entry.getValue()));
        }
        List<BrandAffinity> brands = new ArrayList<>();
        for (Map.Entry<String, Double> entry : brandRemainders.entrySet()) {
            brands.add(new BrandAffinity(entry.getKey(), 1 - entry.getValue()));
        }
        return new PreferenceSnapshot(categories, brands, tasteVectorSum.toTasteVector(), baseTime);
    }

    private double weightOf(PreferenceSignal signal, LocalDateTime baseTime) {
        // 담았다가 뺀 상품이 30일 동안 순서를 끌어당기지 않게 한다
        if (signal.removed()) {
            return 0;
        }
        PreferenceSignalType type = signal.type();
        LocalDateTime windowStart = baseTime.minusDays(type.windowDays());
        if (signal.occurredAt().isAfter(baseTime) || signal.occurredAt().isBefore(windowStart)) {
            return 0;
        }
        double elapsedDays = Duration.between(signal.occurredAt(), baseTime).toSeconds() / SECONDS_PER_DAY;
        return type.intensity() * (1 - elapsedDays / type.windowDays());
    }
}
