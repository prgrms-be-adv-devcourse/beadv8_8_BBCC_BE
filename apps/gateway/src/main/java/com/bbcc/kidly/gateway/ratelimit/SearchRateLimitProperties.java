package com.bbcc.kidly.gateway.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 상품 검색 요청 수 제한(#816). 키(회원 · IP)마다 period 동안 capacity번까지 받는다.
 *
 * @param capacity period 동안 받는 요청 수
 * @param period 한도가 다시 다 차는 시간
 * @param maxKeys 메모리에 들고 있는 키 수의 상한
 */
@ConfigurationProperties("kidly.gateway.rate-limit.search")
public record SearchRateLimitProperties(int capacity, Duration period, int maxKeys) {
}
