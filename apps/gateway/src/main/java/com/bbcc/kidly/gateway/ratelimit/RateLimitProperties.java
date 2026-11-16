package com.bbcc.kidly.gateway.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 한도별 버킷 설정을 한곳에 두어 Gateway 간 같은 정책을 적용한다. */
@ConfigurationProperties("kidly.gateway.rate-limit")
public record RateLimitProperties(Duration switchRefresh, Limit client, Limit total, Limit search, Limit chat) {
    public record Limit(int capacity, Duration period) {
    }
}
