package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.ai.AiFeatureObservationFilter;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Spring AI가 내는 모델 호출 지표(gen_ai.client.operation · 토큰 수)에 추천 AI 기능 이름이 붙는지 확인한다.
 * 세 기능이 같은 모델을 쓰므로, 기능 이름이 없으면 토큰 수를 기능별로 나눌 수 없다.
 */
class AiFeatureObservationFilterTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    @DisplayName("추천 AI 호출 관측 안에서 열린 모델 호출 관측은 부모의 기능 이름(feature)을 이어받는다")
    void copyFeatureFromParentToModelCall() {
        ObservationRegistry observations = ObservationRegistry.create();
        observations.observationConfig()
            .observationHandler(new DefaultMeterObservationHandler(meterRegistry))
            .observationFilter(new AiFeatureObservationFilter());

        Observation.createNotStarted("kidly.recommend.ai.call", observations)
            .lowCardinalityKeyValue("feature", "size_recommendation")
            .observe(() -> Observation.createNotStarted("gen_ai.client.operation", observations).observe(() -> { }));
        Observation.createNotStarted("gen_ai.client.operation", observations).observe(() -> { });

        assertThat(meterRegistry.get("gen_ai.client.operation").tag("feature", "size_recommendation").timer().count())
            .isEqualTo(1);
        assertThat(meterRegistry.find("gen_ai.client.operation").timers())
            .filteredOn(timer -> timer.getId().getTag("feature") == null).hasSize(1);
    }
}
