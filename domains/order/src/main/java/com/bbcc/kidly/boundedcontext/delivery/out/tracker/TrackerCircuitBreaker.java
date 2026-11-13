package com.bbcc.kidly.boundedcontext.delivery.out.tracker;

import com.bbcc.kidly.boundedcontext.delivery.out.config.DeliveryTrackerCircuitBreakerProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * 택배 조회 서킷브레이커(#708, Resilience4j 코어). 레지스트리 · 빈 · 전역 설정 없이 여기서 만들어 쓴다. 상태 조회(10분 주기)용 하나와
 * 구매자 진행 이력 조회(#1320)용 하나({@link #sibling}, 같은 설정 · 따로 세는 인스턴스)를 둔다: 구매자 조회 실패가 배송 완료 반영을 막지 않게.
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
    private static final String TITLE = "택배 조회 서킷";

    private final CircuitBreaker breaker;
    private final Duration openFor;

    private TrackerCircuitBreaker(CircuitBreaker breaker, Duration openFor) {
        this.breaker = breaker;
        this.openFor = openFor;
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
        return of(NAME, TITLE, config, properties.waitDurationInOpenState());
    }

    /**
     * 같은 설정으로 따로 세는 서킷(#1320 구매자 진행 이력). 로그 제목만 다르다.
     */
    TrackerCircuitBreaker sibling(String name, String title) {
        return of(name, title, breaker.getCircuitBreakerConfig(), openFor);
    }

    private static TrackerCircuitBreaker of(String name, String title, CircuitBreakerConfig config, Duration openFor) {
        CircuitBreaker breaker = CircuitBreaker.of(name, config);
        breaker.getEventPublisher().onStateTransition(event -> {
            CircuitBreaker.StateTransition transition = event.getStateTransition();
            CircuitBreaker.Metrics metrics = breaker.getMetrics();
            if (transition.getToState() == CircuitBreaker.State.OPEN) {
                log.warn("{} 열림: from={} failureRate={} slowCallRate={} openFor={}", title,
                    transition.getFromState(), metrics.getFailureRate(), metrics.getSlowCallRate(), openFor);
            } else {
                log.info("{} 상태: from={} to={}", title, transition.getFromState(), transition.getToState());
            }
        });
        return new TrackerCircuitBreaker(breaker, openFor);
    }

    /**
     * 호출을 허락받으면(닫힘 · 반열림 시험 자리) 결과를 기록하며 부르고, 열려 있으면 부르지 않는다.
     *
     * @param beforeCall 허락받은 뒤 호출 전에 할 일(호출 간격 맞추기). 걸린 시간에 넣지 않는다
     * @return 열려 있어 부르지 않았으면 null
     * @throws RuntimeException 호출이 던진 예외 그대로(기록한 뒤)
     */
    <T> T callIfPermitted(Runnable beforeCall, Supplier<T> call) {
        if (!tryAcquirePermission()) {
            return null;
        }
        beforeCall.run();
        return callPermitted(call);
    }

    /**
     * 호출 허락을 받는다(닫힘 · 반열림 시험 자리). 열림 → 반열림 전환도 여기서 일어나므로(자동 전환 꺼짐), 열렸는지 미리 보고 거르지
     * 말고 늘 이것으로 묻는다(#1320). 허락받고 부르지 않게 되면 {@link #releasePermission()}으로 돌려준다.
     */
    boolean tryAcquirePermission() {
        return breaker.tryAcquirePermission();
    }

    /**
     * 받은 허락을 쓰지 않고 돌려준다(반열림 시험 자리가 새지 않게).
     */
    void releasePermission() {
        breaker.releasePermission();
    }

    /**
     * 허락받은 호출을 부르고 결과(성공 · 실패 · 걸린 시간)를 기록한다.
     *
     * @throws RuntimeException 호출이 던진 예외 그대로(기록한 뒤)
     */
    <T> T callPermitted(Supplier<T> call) {
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
