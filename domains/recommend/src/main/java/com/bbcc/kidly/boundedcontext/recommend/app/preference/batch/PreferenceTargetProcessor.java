package com.bbcc.kidly.boundedcontext.recommend.app.preference.batch;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSourceReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceCalculator;
import java.time.LocalDateTime;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/**
 * 대상 하나의 신호와 상품 벡터를 읽어 선호를 계산한다.
 */
public class PreferenceTargetProcessor implements ItemProcessor<Long, PreferenceResult> {

    public PreferenceTargetProcessor(PreferenceTargets targets, PreferenceSourceReader sourceReader,
        PreferenceCalculator calculator, LocalDateTime baseTime) {
    }

    @Override
    public PreferenceResult process(Long targetId) {
        throw new UnsupportedOperationException();
    }
}
