package com.bbcc.kidly.boundedcontext.delivery.out.tracker;

import com.bbcc.kidly.boundedcontext.delivery.out.config.DeliveryTrackerCircuitBreakerProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * 택배 조회 API 전체에 하나인 서킷브레이커(#708, Resilience4j 코어). 레지스트리 · 빈 · 전역 설정 없이 여기서 만들어 쓴다.
 * <ul>
 *   <li>실패로 셈: 네트워크 · 타임아웃 · 5xx · 인증 오류 · 호출 한도 초과 · 응답 이상 등 업무 결과가 아닌 모든 오류</li>
 *   <li>세지 않음: 운송장 형식 오류 · 없는 번호({@link DeliveryTrackerException#isBusinessResult()}). 반열림 시험 호출 자리도 돌려준다</li>
 *   <li>느린 호출: 성공 · 실패와 상관없이 걸린 시간이 기준을 넘으면 느린 호출로 센다</li>
 * </ul>
 * 시각은 주입한 시계로 잰다(테스트가 실제로 기다리지 않게). 상태가 바뀔 때만 로그 한 줄(열림 WARN, 반열림 · 닫힘 INFO)을 남기고
 * 인증값 · 운송장 번호는 남기지 않는다.
 */
@Slf4j
final class TrackerCircuitBreaker {

    private static final String NAME = "delivery-tracker";

    private final CircuitBreaker breaker;

    private TrackerCircuitBreaker(CircuitBreaker breaker) {
        this.breaker = breaker;
    }

    static TrackerCircuitBreaker create(DeliveryTrackerCircuitBreakerProperties properties, Clock clock) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(properties.slidingWindowSize())
            .minimumNumberOfCalls(properties.minimumNumberOfCalls())
            .failureRateThreshold(properties.failureRateThreshold())
            .slowCallDurationThreshold(properties.slowCallDurationThreshold())
            .slowCallRateThreshold(properties.slowCallRateThreshold())
            .waitDurationInOpenState(properties.waitDurationInOpenState())
            .permittedNumberOfCallsInHalfOpenState(properties.permittedNumberOfCallsInHalfOpenState())
            .ignoreException(e -> e instanceof DeliveryTrackerException tracker && tracker.isBusinessResult())
            .clock(clock)
            .currentTimestampFunction(Clock::millis, TimeUnit.MILLISECONDS)
            .build();
        CircuitBreaker breaker = CircuitBreaker.of(NAME, config);
        breaker.getEventPublisher().onStateTransition(event -> {
            CircuitBreaker.StateTransition transition = event.getStateTransition();
            CircuitBreaker.Metrics metrics = breaker.getMetrics();
            if (transition.getToState() == CircuitBreaker.State.OPEN) {
                log.warn("택배 조회 서킷 열림: from={} failureRate={} slowCallRate={} openFor={}",
                    transition.getFromState(), metrics.getFailureRate(), metrics.getSlowCallRate(),
                    properties.waitDurationInOpenState());
            } else {
                log.info("택배 조회 서킷 상태: from={} to={}", transition.getFromState(), transition.getToState());
            }
        });
        return new TrackerCircuitBreaker(breaker);
    }

    /**
     * 호출을 허락받으면(닫힘 · 반열림 시험 자리) 결과를 기록하며 부르고, 열려 있으면 부르지 않는다.
     *
     * @param beforeCall 허락받은 뒤 호출 전에 할 일(호출 간격 맞추기). 걸린 시간에 넣지 않는다
     * @return 열려 있어 부르지 않았으면 null
     * @throws RuntimeException 호출이 던진 예외 그대로(기록한 뒤)
     */
    <T> T callIfPermitted(Runnable beforeCall, Supplier<T> call) {
        if (!breaker.tryAcquirePermission()) {
            return null;
        }
        beforeCall.run();
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
