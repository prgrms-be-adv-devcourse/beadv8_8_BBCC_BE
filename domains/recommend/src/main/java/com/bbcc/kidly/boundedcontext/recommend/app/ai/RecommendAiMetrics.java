package com.bbcc.kidly.boundedcontext.recommend.app.ai;

import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 추천 AI 호출 지표다. 지표 이름 · 태그 값은 이 클래스에만 둔다. 태그에는 기능 · 결과 같은 고정 값만 넣는다(회원 · 자녀 · 상품 ID 금지).
 * dev는 Pod가 자주 다시 뜨고 요청이 적어, 카운터가 처음 오를 때 생기면 Prometheus가 첫 증가를 놓친다.
 * 그래서 기동할 때 모든 기능 · 결과 조합의 카운터를 0으로 만들어 둔다.
 */
@Component
public class RecommendAiMetrics {

    static final String REQUESTS = "kidly.recommend.ai.requests";
    static final String STORE_LOOKUPS = "kidly.recommend.ai.store.lookups";
    static final String REJECTED = "kidly.recommend.ai.rejected";

    /**
     * AI 요청의 결과: 저장한 답을 씀 · AI가 답함 · 기다리다 시간 초과 · 오류 · 하루 상한에 걸림.
     */
    public enum RequestResult { STORED, ANSWERED, TIMEOUT, ERROR, DAILY_LIMIT }

    /**
     * 저장한 답 조회 결과: 있음 · 없음 · 오류.
     */
    public enum LookupResult { HIT, MISS, ERROR }

    private final MeterRegistry meterRegistry;
    // 호출 시간 Timer를 Observation API로 재는 다음 단계에서 쓴다
    private final ObservationRegistry observationRegistry;
    private final Map<AiResponseType, Map<RequestResult, Counter>> requests = new EnumMap<>(AiResponseType.class);
    private final Map<AiResponseType, Map<LookupResult, Counter>> storeLookups = new EnumMap<>(AiResponseType.class);
    private final Map<AiResponseType, Counter> rejected = new EnumMap<>(AiResponseType.class);

    public RecommendAiMetrics(MeterRegistry meterRegistry, ObservationRegistry observationRegistry) {
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
        for (AiResponseType feature : AiResponseType.values()) {
            requests.put(feature, registerRequests(feature));
            storeLookups.put(feature, registerStoreLookups(feature));
            rejected.put(feature, registerRejected(feature));
        }
    }

    public void recordRequest(AiResponseType feature, RequestResult result) {
        requests.get(feature).get(result).increment();
    }

    public void recordLookup(AiResponseType feature, LookupResult result) {
        storeLookups.get(feature).get(result).increment();
    }

    private Map<RequestResult, Counter> registerRequests(AiResponseType feature) {
        Map<RequestResult, Counter> counters = new EnumMap<>(RequestResult.class);
        for (RequestResult result : RequestResult.values()) {
            counters.put(result, Counter.builder(REQUESTS)
                .description("추천 AI 요청 횟수. result는 stored(저장한 답) · answered(AI 답) · timeout · error · daily_limit")
                .tag("feature", tagValue(feature))
                .tag("result", tagValue(result))
                .register(meterRegistry));
        }
        return counters;
    }

    private Map<LookupResult, Counter> registerStoreLookups(AiResponseType feature) {
        Map<LookupResult, Counter> counters = new EnumMap<>(LookupResult.class);
        for (LookupResult result : LookupResult.values()) {
            counters.put(result, Counter.builder(STORE_LOOKUPS)
                .description("추천 AI 저장 답 조회 횟수. result는 hit(있음) · miss(없음) · error")
                .tag("feature", tagValue(feature))
                .tag("result", tagValue(result))
                .register(meterRegistry));
        }
        return counters;
    }

    private Counter registerRejected(AiResponseType feature) {
        return Counter.builder(REJECTED)
            .description("AI 답이 검증에서 버려져 규칙 결과로 답한 횟수")
            .tag("feature", tagValue(feature))
            .register(meterRegistry);
    }

    static String tagValue(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
