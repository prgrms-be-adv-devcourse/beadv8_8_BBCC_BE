package com.bbcc.kidly.boundedcontext.recommend.app.preference.batch;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSourceReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceCalculator;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSignal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/**
 * 대상 하나의 신호와 상품 벡터를 읽어 선호를 계산한다.
 */
public class PreferenceTargetProcessor implements ItemProcessor<Long, PreferenceResult> {

    private final PreferenceTargets targets;
    private final PreferenceSourceReader sourceReader;
    private final PreferenceCalculator calculator;
    private final LocalDateTime baseTime;

    public PreferenceTargetProcessor(PreferenceTargets targets, PreferenceSourceReader sourceReader,
        PreferenceCalculator calculator, LocalDateTime baseTime) {
        this.targets = requireNonNull(targets, "targets는 필수입니다");
        this.sourceReader = requireNonNull(sourceReader, "sourceReader는 필수입니다");
        this.calculator = requireNonNull(calculator, "calculator는 필수입니다");
        this.baseTime = requireNonNull(baseTime, "baseTime은 필수입니다");
    }

    @Override
    public PreferenceResult process(Long targetId) {
        List<PreferenceSignal> signals = targets.findSignals(targetId, baseTime);
        Map<Long, EmbeddingVector> embeddings = sourceReader.findEmbeddings(tasteProductIds(signals));
        return new PreferenceResult(targetId, calculator.calculate(signals, embeddings, baseTime));
    }

    // 1536차원 벡터라 취향 벡터에 넣지 않는 구매 · 검색 상품까지 읽지 않는다
    private static Set<Long> tasteProductIds(List<PreferenceSignal> signals) {
        Set<Long> productIds = new LinkedHashSet<>();
        for (PreferenceSignal signal : signals) {
            if (signal.type().countsForTaste() && signal.productId() != null) {
                productIds.add(signal.productId());
            }
        }
        return productIds;
    }
}
