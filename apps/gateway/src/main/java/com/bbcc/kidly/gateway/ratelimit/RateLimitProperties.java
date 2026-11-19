package com.bbcc.kidly.gateway.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 한도별 버킷 설정을 한곳에 두어 Gateway 간 같은 정책을 적용한다.
 * clientEnabled는 회원 · IP별 한도만 끈다. 검색 · 챗봇은 OpenAI 비용 방어라 끄는 설정이 없다.
 */
@ConfigurationProperties("kidly.gateway.rate-limit")
public record RateLimitProperties(@DefaultValue("true") boolean clientEnabled, Limit client, Limit search, Limit chat) {
    public record Limit(int capacity, Duration period) {
    }
}
