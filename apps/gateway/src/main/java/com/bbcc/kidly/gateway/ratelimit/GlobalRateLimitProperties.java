package com.bbcc.kidly.gateway.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("kidly.gateway.rate-limit.global")
public record GlobalRateLimitProperties(int capacity, Duration period, Duration switchRefresh) {
}
