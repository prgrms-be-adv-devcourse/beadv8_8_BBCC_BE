package com.bbcc.kidly.global.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

/**
 * 메일 발송기를 고른다. 기본은 켬(kidly.mail.enabled=true)이고, 끄려면 MAIL_ENABLED=false로 둔다.
 * 켜져 있어도 SMTP 계정(MAIL_USERNAME · MAIL_PASSWORD)이 없으면 앱은 그대로 띄우고 로그 발송기를 쓴다 (계정이 없는 로컬 · 서버)
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    @Bean
    EmailSender emailSender(
        MailProperties properties,
        ObjectProvider<JavaMailSender> javaMailSender,
        @Value("${spring.mail.username:}") String username,
        @Value("${spring.mail.password:}") String password) {
        if (!properties.enabled()) {
            return new LoggingEmailSender();
        }
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.warn("메일 발송이 켜져 있지만 SMTP 계정(MAIL_USERNAME · MAIL_PASSWORD)이 없어 보내지 않고 로그만 남깁니다");
            return new LoggingEmailSender();
        }
        String from = StringUtils.hasText(properties.from()) ? properties.from() : username;
        return new SmtpEmailSender(javaMailSender.getObject(), from);
    }
}
