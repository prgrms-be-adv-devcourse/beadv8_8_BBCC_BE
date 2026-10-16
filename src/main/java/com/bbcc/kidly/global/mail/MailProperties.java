package com.bbcc.kidly.global.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled 실제로 보낼지 (기본 true). false면 로그만 남긴다
 * @param from    보내는 주소. 비면 SMTP 아이디(spring.mail.username)
 */
@ConfigurationProperties("kidly.mail")
public record MailProperties(boolean enabled, String from) {
}
