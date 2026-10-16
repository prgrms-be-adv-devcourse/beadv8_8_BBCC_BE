package com.bbcc.kidly.global.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled 실제로 보낼지. false면 로그만 남긴다
 * @param from    보내는 주소 (켰으면 필수)
 */
@ConfigurationProperties("kidly.mail")
public record MailProperties(boolean enabled, String from) {
}
