package com.bbcc.kidly.boundedcontext.auth.in;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param secureCookie https에서만 쿠키를 보낸다. 설정을 빠뜨려도 켜지도록 기본값이 true이고, http인 local에서만 끈다
 */
@ConfigurationProperties("kidly.security")
public record SecurityProperties(@DefaultValue("true") boolean secureCookie) {
}
