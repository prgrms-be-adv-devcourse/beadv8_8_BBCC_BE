package com.bbcc.kidly.global.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

/**
 * kidly.mail.enabled에 따라 SMTP 발송기와 로그 발송기 중 하나를 등록한다.
 * 켰는데 보내는 주소가 없으면 기동을 막아 설정 누락을 배포 때 바로 알게 한다
 */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    @Bean
    EmailSender emailSender(MailProperties properties, ObjectProvider<JavaMailSender> javaMailSender) {
        if (!properties.enabled()) {
            return new LoggingEmailSender();
        }
        if (!StringUtils.hasText(properties.from())) {
            throw new IllegalStateException("메일 발송을 켰지만 보내는 주소(kidly.mail.from)가 없습니다");
        }
        return new SmtpEmailSender(javaMailSender.getObject(), properties.from());
    }
}
