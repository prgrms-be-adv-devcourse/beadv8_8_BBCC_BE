package com.bbcc.kidly.boundedcontext.notification.app;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 알림 설정. 값이 비었거나 주소 형식이 아니면 기동에 실패한다.
 *
 * @param siteUrl 메일 본문에 넣는 사이트 주소. 환경변수가 없을 때 바인더가 남기는 `${NOTIFICATION_SITE_URL}`
 *     같은 글자도 걸러내도록 http(s)로 시작하고 공백이 없는 값만 받는다
 */
@Validated
@ConfigurationProperties("kidly.notification")
public record NotificationProperties(@NotBlank @Pattern(regexp = "https?://\\S+") String siteUrl) {
}
