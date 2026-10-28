package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.app.port.Email;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationEmailSender;
import com.bbcc.kidly.boundedcontext.notification.out.mail.LoggingEmailSender;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

@IntegrationTest
class NotificationMailConfigIntegrationTest {

    // 테스트에서는 FakeNotificationEmailSender가 @Primary라, 설정이 고른 발송기는 빈 이름으로 꺼낸다
    @Autowired
    @Qualifier("notificationEmailSender")
    private NotificationEmailSender notificationEmailSender;

    @Test
    @DisplayName("테스트 프로필(꺼짐)에서는 로그 발송기가 등록되고, 보내도 실패하지 않는다")
    void loggingByDefault() {
        assertThat(notificationEmailSender).isInstanceOf(LoggingEmailSender.class);

        notificationEmailSender.send(new Email("parent@kidly.dev", "제목", "본문"));
    }
}
