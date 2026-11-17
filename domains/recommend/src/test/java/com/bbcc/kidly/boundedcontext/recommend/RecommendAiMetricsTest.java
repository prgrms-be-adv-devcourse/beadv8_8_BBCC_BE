package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.ai.RecommendAiMetrics;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 추천 AI 지표가 기능(feature)마다 나뉘어 등록되는지 확인한다.
 * dev는 Pod가 자주 다시 뜨고 요청이 적어, 카운터가 처음 오를 때 생기면 Prometheus가 첫 증가를 놓친다. 그래서 기동할 때 0으로 만든다.
 */
class RecommendAiMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @ParameterizedTest
    @ValueSource(strings = {"size_recommendation", "home_preview", "bought_together"})
    @DisplayName("기동하면 기능마다 결과 · 저장 답 조회 · 검증 버림 카운터가 모두 0으로 있다")
    void registerZeroCountersOnStart(String feature) {
        new RecommendAiMetrics(registry, ObservationRegistry.NOOP);

        for (String result : new String[] {"stored", "answered", "timeout", "error", "daily_limit"}) {
            assertThat(registry.get("kidly.recommend.ai.requests").tag("feature", feature).tag("result", result)
                .counter().count()).isZero();
        }
        for (String result : new String[] {"hit", "miss", "error"}) {
            assertThat(registry.get("kidly.recommend.ai.store.lookups").tag("feature", feature).tag("result", result)
                .counter().count()).isZero();
        }
        assertThat(registry.get("kidly.recommend.ai.rejected").tag("feature", feature).counter().count()).isZero();
    }

    @Test
    @DisplayName("기동하면 정해진 기능 · 결과 조합 말고 다른 카운터는 만들지 않는다")
    void registerOnlyKnownCombinations() {
        new RecommendAiMetrics(registry, ObservationRegistry.NOOP);

        assertThat(registry.find("kidly.recommend.ai.requests").counters()).hasSize(15);
        assertThat(registry.find("kidly.recommend.ai.store.lookups").counters()).hasSize(9);
        assertThat(registry.find("kidly.recommend.ai.rejected").counters()).hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"size_recommendation", "home_preview", "bought_together"})
    @DisplayName("기동하면 기능마다 AI 호출 시간 Timer가 호출 0번으로 있다")
    void registerZeroCallTimerOnStart(String feature) {
        new RecommendAiMetrics(registry, ObservationRegistry.NOOP);

        assertThat(registry.get("kidly.recommend.ai.call").tag("feature", feature).timer().count()).isZero();
    }

    @Test
    @DisplayName("AI를 호출하면 기동 때 만든 Timer의 횟수가 오르고, 같은 기능의 Timer가 따로 생기지 않는다")
    void countCallOnTimerRegisteredOnStart() {
        ObservationRegistry observations = ObservationRegistry.create();
        observations.observationConfig().observationHandler(new DefaultMeterObservationHandler(registry));
        RecommendAiMetrics metrics = new RecommendAiMetrics(registry, observations);

        metrics.observeCall(AiResponseType.SIZE_RECOMMENDATION, () -> "답").get();

        assertThat(registry.find("kidly.recommend.ai.call").tag("feature", "size_recommendation").timers())
            .singleElement()
            .satisfies(timer -> assertThat(timer.count()).isEqualTo(1));
    }

    @Test
    @DisplayName("AI 호출 시간은 AI 대기 경계(3 · 3.5 · 4초 등)에 맞춘 구간으로 세어, 제한을 넘은 비율을 정확히 구할 수 있다")
    void bucketCallTimeAtTimeoutBoundaries() {
        registry.config().meterFilter(RecommendAiMetrics.callTimeBuckets());
        ObservationRegistry observations = ObservationRegistry.create();
        observations.observationConfig().observationHandler(new DefaultMeterObservationHandler(registry));
        RecommendAiMetrics metrics = new RecommendAiMetrics(registry, observations);

        metrics.observeCall(AiResponseType.SIZE_RECOMMENDATION, () -> "답").get();

        double[] boundaries = Arrays.stream(registry.get("kidly.recommend.ai.call").timer().takeSnapshot()
            .histogramCounts()).mapToDouble(bucket -> bucket.bucket(TimeUnit.SECONDS)).toArray();
        assertThat(boundaries).containsExactly(1, 2, 3, 3.5, 4, 4.5, 5, 6, 8, 10);
    }
}
