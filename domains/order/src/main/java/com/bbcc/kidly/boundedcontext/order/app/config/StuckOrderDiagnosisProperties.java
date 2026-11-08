package com.bbcc.kidly.boundedcontext.order.app.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 멈춘 주문 AI 진단(#926)의 요청 수 설정. 기본값은 order.yml. 연결 · 제공자 설정은 out/config의 OrderAiProperties(같은 kidly.order.ai).
 *
 * @param dailyLimit 하루(서버 시각 0시부터) 요청 상한. 성공 · 실패 모두 센다. 0이면 요청하지 않는다
 * @param maxPerRun 한 실행에서 요청하는 최대 수
 * @param scanSize 멈춘 대상을 읽는 한 페이지 크기(오래된 것부터)
 * @param maxScanPages 한 실행에서 종류마다 읽는 최대 페이지 수. 앞쪽이 모두 이미 요청한 건이어도 요청 상한이 남으면 다음 페이지를 읽는다
 */
@Validated
@ConfigurationProperties("kidly.order.ai")
public record StuckOrderDiagnosisProperties(
    @Min(0) int dailyLimit,
    @Min(1) @Max(100) int maxPerRun,
    @Min(1) @Max(1000) int scanSize,
    @Min(1) @Max(1000) int maxScanPages) {
}
