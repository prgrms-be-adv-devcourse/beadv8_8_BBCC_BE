package com.bbcc.kidly.global.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;

class MailConfigTest {

    private final MailConfig config = new MailConfig();
    private final StaticListableBeanFactory beans = new StaticListableBeanFactory();

    @Test
    @DisplayName("꺼져 있으면 보내지 않고 로그만 남기는 발송기를 쓴다")
    void disabled() {
        EmailSender sender = config.emailSender(
            new MailProperties(false, ""), beans.getBeanProvider(JavaMailSender.class));

        assertThat(sender).isInstanceOf(LoggingEmailSender.class);
    }

    @Test
    @DisplayName("켜져 있으면 SMTP 발송기를 쓴다")
    void enabled() {
        beans.addBean("javaMailSender", mock(JavaMailSender.class));

        EmailSender sender = config.emailSender(
            new MailProperties(true, "kidly.team@gmail.com"), beans.getBeanProvider(JavaMailSender.class));

        assertThat(sender).isInstanceOf(SmtpEmailSender.class);
    }

    @Test
    @DisplayName("켰는데 보내는 주소가 없으면 앱이 뜨지 않게 실패한다")
    void enabledWithoutFrom() {
        beans.addBean("javaMailSender", mock(JavaMailSender.class));

        assertThatThrownBy(() -> config.emailSender(
            new MailProperties(true, " "), beans.getBeanProvider(JavaMailSender.class)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("kidly.mail.from");
    }
}
