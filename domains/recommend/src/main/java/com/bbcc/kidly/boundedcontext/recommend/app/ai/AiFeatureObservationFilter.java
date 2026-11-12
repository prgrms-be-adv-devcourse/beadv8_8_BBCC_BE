package com.bbcc.kidly.boundedcontext.recommend.app.ai;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.stereotype.Component;

/**
 * Spring AI가 내는 모델 호출 관측(이름이 gen_ai.로 시작)에 추천 AI 기능 이름(feature)을 붙인다.
 * 세 기능이 같은 모델을 쓰므로, 이 이름이 없으면 토큰 수를 기능별로 나눌 수 없다.
 * 기능 이름은 모델 호출 관측의 부모인 추천 AI 호출 관측에서 가져온다.
 */
@Component
public class AiFeatureObservationFilter implements ObservationFilter {

    private static final String MODEL_CALL_PREFIX = "gen_ai.";

    @Override
    public Observation.Context map(Observation.Context context) {
        if (!context.getName().startsWith(MODEL_CALL_PREFIX)
            || context.getLowCardinalityKeyValue(RecommendAiMetrics.FEATURE_KEY) != null) {
            return context;
        }
        Observation.ContextView ancestor = context.getParentObservation() == null
            ? null : context.getParentObservation().getContextView();
        while (ancestor != null) {
            if (RecommendAiMetrics.AI_CALL.equals(ancestor.getName())) {
                KeyValue feature = ancestor.getLowCardinalityKeyValue(RecommendAiMetrics.FEATURE_KEY);
                if (feature != null) {
                    context.addLowCardinalityKeyValue(feature);
                }
                return context;
            }
            ancestor = ancestor.getParentObservation() == null
                ? null : ancestor.getParentObservation().getContextView();
        }
        return context;
    }
}
