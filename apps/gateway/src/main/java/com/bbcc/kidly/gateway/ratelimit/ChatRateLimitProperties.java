package com.bbcc.kidly.gateway.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 챗봇 질문 요청 수 제한(#1191). 키(회원 · IP)마다 period 동안 capacity번까지 받는다.
 * 버킷이 capacity개로 시작하므로 capacity는 한꺼번에 보낼 수 있는 상한(burst)이기도 하다.
 *
 * @param capacity period 동안 받는 요청 수
 * @param period 한도가 다시 다 차는 시간
 * @param maxKeys 메모리에 들고 있는 키 수의 상한
 */
@ConfigurationProperties("kidly.gateway.rate-limit.chat")
public record ChatRateLimitProperties(int capacity, Duration period, int maxKeys) {
}
