package com.bbcc.kidly.boundedcontext.order.out.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 멈춘 주문 AI 진단(#926)의 제공자 연결 설정. 기본값은 order.yml(Groq의 OpenAI 호환 주소 · 모델). 요청 수 상한은 app/config의
 * StuckOrderDiagnosisProperties(같은 kidly.order.ai).
 * provider · base-url · model만 바꿔 OpenAI 호환 제공자를 바꾼다. 호환되지 않는 제공자는 out/ai에 어댑터를 더하고 provider 값을 더한다.
 *
 * @param enabled 스위치(기본 false). 켜도 openai-compatible에 키가 없으면(또는 헤더에 못 넣는 값이면) 꺼진다
 * @param apiKey 환경변수 ORDER_AI_API_KEY. 로그 · toString에 남기지 않는다
 * @param timeout 읽기 제한 시간(소켓 읽기 한 번마다, 응답 전체 시간이 아님). 느리게 조금씩 오는 응답은 서킷브레이커의
 *     느린 호출 기준(slow-call-duration-threshold)이 잡는다
 * @param maxOutputTokens 모델 출력 상한(요청의 max_completion_tokens)
 */
@Validated
@ConfigurationProperties("kidly.order.ai")
public record OrderAiProperties(
    boolean enabled,
    @NotNull Provider provider,
    @NotBlank String baseUrl,
    @NotBlank String model,
    String apiKey,
    @NotNull Duration connectTimeout,
    @NotNull Duration timeout,
    @Min(1) int maxOutputTokens,
    @NotNull @Valid CircuitBreaker circuitBreaker) {

    public enum Provider {
        /** OpenAI 호환 Chat Completions(Groq · OpenAI 등) */
        OPENAI_COMPATIBLE,
        /** 외부 호출 없이 정해진 메모를 돌려준다(로컬 · 테스트) */
        FAKE
    }

    /**
     * 서킷브레이커(#708 방식). 의미는 DeliveryTrackerCircuitBreakerProperties와 같다.
     */
    public record CircuitBreaker(
        @Min(1) int slidingWindowSize,
        @Min(1) int minimumNumberOfCalls,
        @DecimalMin("1") @DecimalMax("100") float failureRateThreshold,
        @NotNull Duration slowCallDurationThreshold,
        @DecimalMin("1") @DecimalMax("100") float slowCallRateThreshold,
        @NotNull Duration waitDurationInOpenState,
        @Min(1) int permittedNumberOfCallsInHalfOpenState) {
    }

    /**
     * 실제로 켤지: 스위치가 켜졌고, 외부 제공자면 키가 있다.
     */
    public boolean active() {
        return enabled && (provider == Provider.FAKE || hasUsableKey());
    }

    public boolean hasUsableKey() {
        // 헤더에 못 넣는 값이면 JDK 예외 메시지에 헤더 값(키)이 찍힐 수 있어 미리 막는다(택배 조회와 같음)
        return apiKey != null && !apiKey.isBlank() && apiKey.chars().allMatch(c -> c > 0x20 && c < 0x7F);
    }

    // record 기본 toString은 키를 그대로 찍으므로 가린다
    @Override
    public String toString() {
        return "OrderAiProperties[enabled=" + enabled + ", provider=" + provider + ", baseUrl=" + baseUrl
            + ", model=" + model + ", active=" + active() + "]";
    }
}
