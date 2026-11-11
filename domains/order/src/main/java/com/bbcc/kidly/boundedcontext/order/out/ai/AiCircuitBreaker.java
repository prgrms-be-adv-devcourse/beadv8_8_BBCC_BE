package com.bbcc.kidly.boundedcontext.order.out.ai;

import com.bbcc.kidly.boundedcontext.order.out.config.OrderAiProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 진단 호출 전체에 하나인 서킷브레이커(#926, #708 방식: Resilience4j 코어, 레지스트리 · 빈 · 전역 설정 없이 여기서 만든다).
 * 형식 오류를 포함한 모든 호출 실패를 실패로 센다. 걸린 시간이 기준을 넘으면 느린 호출. 상태가 바뀔 때만 로그 한 줄(열림 WARN).
 */
@Slf4j
final class AiCircuitBreaker {

    private static final String NAME = "order-ai";

    private final CircuitBreaker breaker;

    private AiCircuitBreaker(CircuitBreaker breaker) {
        this.breaker = breaker;
    }

    static AiCircuitBreaker create(OrderAiProperties.CircuitBreaker properties, Clock clock) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(properties.slidingWindowSize())
            .minimumNumberOfCalls(properties.minimumNumberOfCalls())
            .failureRateThreshold(properties.failureRateThreshold())
            .slowCallDurationThreshold(properties.slowCallDurationThreshold())
            .slowCallRateThreshold(properties.slowCallRateThreshold())
            .waitDurationInOpenState(properties.waitDurationInOpenState())
            .permittedNumberOfCallsInHalfOpenState(properties.permittedNumberOfCallsInHalfOpenState())
            .clock(clock)
            .currentTimestampFunction(Clock::millis, TimeUnit.MILLISECONDS)
            .build();
        CircuitBreaker breaker = CircuitBreaker.of(NAME, config);
        breaker.getEventPublisher().onStateTransition(event -> {
            CircuitBreaker.StateTransition transition = event.getStateTransition();
            if (transition.getToState() == CircuitBreaker.State.OPEN) {
                log.warn("AI 진단 서킷 열림: from={} failureRate={} slowCallRate={} openFor={}",
                    transition.getFromState(), breaker.getMetrics().getFailureRate(),
                    breaker.getMetrics().getSlowCallRate(), properties.waitDurationInOpenState());
            } else {
                log.info("AI 진단 서킷 상태: from={} to={}", transition.getFromState(), transition.getToState());
            }
        });
        return new AiCircuitBreaker(breaker);
    }

    /**
     * @return 열려 있어 부르지 않았으면 null
     * @throws RuntimeException 호출이 던진 예외 그대로(기록한 뒤)
     */
    <T> T callIfPermitted(Supplier<T> call) {
        if (!breaker.tryAcquirePermission()) {
            return null;
        }
        long start = breaker.getCurrentTimestamp();
        try {
            T result = call.get();
            breaker.onSuccess(breaker.getCurrentTimestamp() - start, breaker.getTimestampUnit());
            return result;
        } catch (RuntimeException e) {
            breaker.onError(breaker.getCurrentTimestamp() - start, breaker.getTimestampUnit(), e);
            throw e;
        }
    }

    CircuitBreaker.State state() {
        return breaker.getState();
    }
}
