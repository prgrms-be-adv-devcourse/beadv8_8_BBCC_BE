package com.bbcc.kidly.boundedcontext.member.app;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Refresh Token 설정.
 *
 * @param validity 유효 기간. 재발급할 때마다 다시 이 기간이 된다
 * @param secureCookie https에서만 쿠키를 보낸다. 설정을 빠뜨려도 켜지도록 기본값이 true이고, http인 local에서만 끈다
 */
@ConfigurationProperties("refresh-token")
public record RefreshTokenProperties(Duration validity, @DefaultValue("true") boolean secureCookie) {
}
