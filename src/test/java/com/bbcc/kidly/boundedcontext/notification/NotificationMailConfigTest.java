package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationEmailSender;
import com.bbcc.kidly.boundedcontext.notification.out.mail.LoggingEmailSender;
import com.bbcc.kidly.boundedcontext.notification.out.mail.NotificationMailConfig;
import com.bbcc.kidly.boundedcontext.notification.out.mail.NotificationMailProperties;
import com.bbcc.kidly.boundedcontext.notification.out.mail.SmtpEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;

class NotificationMailConfigTest {

    private static final String ACCOUNT = "kidly.team@gmail.com";
    private static final String APP_PASSWORD = "abcdefghijklmnop";

    private final NotificationMailConfig config = new NotificationMailConfig();
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
        NotificationEmailSender sender = config.notificationEmailSender(
            new NotificationMailProperties(true, ""), javaMailSender, ACCOUNT, APP_PASSWORD);

        assertThat(sender).isInstanceOf(SmtpEmailSender.class);
    }

    @ParameterizedTest
    @CsvSource(value = {"'', abcdefghijklmnop", "kidly.team@gmail.com, ''", "'', ''"})
    @DisplayName("켜져 있어도 계정(아이디 · 앱 비밀번호)이 없으면 앱은 뜨고, 보내지 않고 로그만 남긴다")
    void enabledWithoutAccount(String username, String password) {
        NotificationEmailSender sender = config.notificationEmailSender(
            new NotificationMailProperties(true, ""), javaMailSender, username, password);

        assertThat(sender).isInstanceOf(LoggingEmailSender.class);
    }

    @Test
    @DisplayName("끄면 계정이 있어도 보내지 않고 로그만 남긴다")
    void disabled() {
        NotificationEmailSender sender = config.notificationEmailSender(
            new NotificationMailProperties(false, ""), javaMailSender, ACCOUNT, APP_PASSWORD);

        assertThat(sender).isInstanceOf(LoggingEmailSender.class);
    }
}
