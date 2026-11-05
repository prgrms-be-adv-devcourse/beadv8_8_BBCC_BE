package com.bbcc.kidly.boundedcontext.auth.app;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Refresh Token 설정.
 *
 * @param validity 유효 기간. 재발급할 때마다 다시 이 기간이 된다
 */
@ConfigurationProperties("refresh-token")
public record RefreshTokenProperties(Duration validity) {
}
