package com.bbcc.kidly.boundedcontext.auth.app;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("refresh-token")
public record RefreshTokenProperties(Duration validity) {
}
