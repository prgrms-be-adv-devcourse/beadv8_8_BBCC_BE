package com.bbcc.kidly.boundedcontext.delivery.out.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 택배 조회 호출의 서킷브레이커(#708). 택배 조회 API 전체에 하나(택배사별로 나누지 않음). 기본값은 order.yml 한 곳에만 둔다.
 * 전역 resilience4j.* 설정 · 스타터를 쓰지 않고 이 값으로 out/tracker 안에서 만든다(같은 앱의 다른 도메인과 섞이지 않게).
 *
 * @param slidingWindowSize 판단 창(최근 호출 수)
 * @param minimumNumberOfCalls 판단을 시작하는 최소 호출 수
 * @param failureRateThreshold 이 실패율(%) 이상이면 열림
 * @param slowCallDurationThreshold 이 시간을 넘긴 호출은 느린 호출
 * @param slowCallRateThreshold 느린 호출 비율(%)이 이 값 이상이면 열림
 * @param waitDurationInOpenState 열림 유지 시간. 지나면 다음 호출 때 반열림
 * @param permittedNumberOfCallsInHalfOpenState 반열림에서 시험하는 호출 수
 */
@Validated
@ConfigurationProperties("kidly.order.delivery-tracker.circuit-breaker")
public record DeliveryTrackerCircuitBreakerProperties(
    @Min(1) int slidingWindowSize,
    @Min(1) int minimumNumberOfCalls,
    @DecimalMin("1") @DecimalMax("100") float failureRateThreshold,
    @NotNull Duration slowCallDurationThreshold,
    @DecimalMin("1") @DecimalMax("100") float slowCallRateThreshold,
    @NotNull Duration waitDurationInOpenState,
    @Min(1) int permittedNumberOfCallsInHalfOpenState) {
}
