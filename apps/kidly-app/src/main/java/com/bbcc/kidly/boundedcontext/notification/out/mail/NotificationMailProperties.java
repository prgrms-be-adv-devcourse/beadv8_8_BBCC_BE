package com.bbcc.kidly.boundedcontext.notification.out.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 알림 메일 발송 설정.
 *
 * @param enabled 실제로 보낼지 (기본 true). false면 로그만 남긴다
 * @param from    보내는 주소. 비면 SMTP 아이디(spring.mail.username)
 */
@ConfigurationProperties("kidly.mail")
public record NotificationMailProperties(boolean enabled, String from) {
}
