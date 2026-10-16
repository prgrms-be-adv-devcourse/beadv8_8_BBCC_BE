package com.bbcc.kidly.global.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;

class MailConfigTest {

    private static final String ACCOUNT = "kidly.team@gmail.com";
    private static final String APP_PASSWORD = "abcdefghijklmnop";

    private final MailConfig config = new MailConfig();
    private ObjectProvider<JavaMailSender> javaMailSender;

    @BeforeEach
    void setUp() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("javaMailSender", mock(JavaMailSender.class));
        javaMailSender = beans.getBeanProvider(JavaMailSender.class);
    }

    @Test
    @DisplayName("켜져 있고 계정이 있으면 SMTP 발송기를 쓴다")
    void enabledWithAccount() {
        EmailSender sender = config.emailSender(new MailProperties(true, ""), javaMailSender, ACCOUNT, APP_PASSWORD);

        assertThat(sender).isInstanceOf(SmtpEmailSender.class);
    }

    @ParameterizedTest
    @CsvSource(value = {"'', abcdefghijklmnop", "kidly.team@gmail.com, ''", "'', ''"})
    @DisplayName("켜져 있어도 계정(아이디 · 앱 비밀번호)이 없으면 앱은 뜨고, 보내지 않고 로그만 남긴다")
    void enabledWithoutAccount(String username, String password) {
        EmailSender sender = config.emailSender(new MailProperties(true, ""), javaMailSender, username, password);

        assertThat(sender).isInstanceOf(LoggingEmailSender.class);
    }

    @Test
    @DisplayName("끄면 계정이 있어도 보내지 않고 로그만 남긴다")
    void disabled() {
        EmailSender sender = config.emailSender(new MailProperties(false, ""), javaMailSender, ACCOUNT, APP_PASSWORD);

        assertThat(sender).isInstanceOf(LoggingEmailSender.class);
    }
}
