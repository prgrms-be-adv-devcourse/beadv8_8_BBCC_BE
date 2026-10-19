package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.notification.app.port.EmailSendException;
import com.bbcc.kidly.boundedcontext.notification.out.mail.NotificationEmailSenderAdapter;
import com.bbcc.kidly.global.mail.Email;
import com.bbcc.kidly.global.mail.EmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;

@ExtendWith(MockitoExtension.class)
class NotificationEmailSenderAdapterTest {

    private static final Email EMAIL = new Email("parent@kidly.co.kr", "제목", "본문");

    @Mock
    private EmailSender emailSender;

    @InjectMocks
    private NotificationEmailSenderAdapter notificationEmailSenderAdapter;

    @Test
    @DisplayName("global.mail 발송기로 그대로 보낸다")
    void send() {
        notificationEmailSenderAdapter.send(EMAIL);

        verify(emailSender).send(EMAIL);
    }

    @Test
    @DisplayName("SMTP 인증 실패와 주소 형식 오류는 다시 보내도 같아서 재시도하지 않는다")
    void permanentFailures() {
        var authFailure = new MailAuthenticationException("인증 실패 parent@kidly.co.kr");
        willThrow(authFailure).given(emailSender).send(EMAIL);
        assertThatThrownBy(() -> notificationEmailSenderAdapter.send(EMAIL))
            .isInstanceOfSatisfying(EmailSendException.class, e -> {
                assertThat(e.isRetryable()).isFalse();
                assertThat(e).hasMessage("SMTP 인증 실패").hasCause(authFailure);
            });

        var parseFailure = new MailParseException("형식 오류 parent@kidly.co.kr");
        willThrow(parseFailure).given(emailSender).send(EMAIL);
        assertThatThrownBy(() -> notificationEmailSenderAdapter.send(EMAIL))
            .isInstanceOfSatisfying(EmailSendException.class, e -> {
                assertThat(e.isRetryable()).isFalse();
                assertThat(e).hasMessage("메일 주소 · 형식 오류").hasCause(parseFailure);
            });
    }

    @Test
    @DisplayName("연결 실패 같은 그 밖의 메일 오류는 재시도한다")
    void retryableFailure() {
        var sendFailure = new MailSendException("550 5.1.1 <parent@kidly.co.kr> User unknown");
        willThrow(sendFailure).given(emailSender).send(EMAIL);

        assertThatThrownBy(() -> notificationEmailSenderAdapter.send(EMAIL))
            .isInstanceOfSatisfying(EmailSendException.class, e -> {
                assertThat(e.isRetryable()).isTrue();
                assertThat(e).hasMessage("메일 발송 실패: MailSendException").hasCause(sendFailure);
            });
    }
}
