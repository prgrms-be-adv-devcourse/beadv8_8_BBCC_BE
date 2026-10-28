package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.bbcc.kidly.boundedcontext.notification.app.port.Email;
import com.bbcc.kidly.boundedcontext.notification.app.port.EmailSendException;
import com.bbcc.kidly.boundedcontext.notification.out.mail.SmtpEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderTest {

    private static final String FROM = "kidly.team@gmail.com";
    private static final Email EMAIL = new Email("parent@kidly.co.kr", "제목", "본문");

    @Mock
    private JavaMailSender javaMailSender;

    private SmtpEmailSender sender;

    @BeforeEach
    void setUp() {
        sender = new SmtpEmailSender(javaMailSender, FROM);
    }

    @Test
    @DisplayName("보내는 주소 · 받는 사람 · 제목 · 본문을 담아 SMTP로 보낸다")
    void send() {
        sender.send(new Email("parent@kidly.dev", "결제가 완료됐어요", "주문 #1 결제가 완료됐어요."));

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        then(javaMailSender).should().send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo(FROM);
        assertThat(message.getValue().getTo()).containsExactly("parent@kidly.dev");
        assertThat(message.getValue().getSubject()).isEqualTo("결제가 완료됐어요");
        assertThat(message.getValue().getText()).isEqualTo("주문 #1 결제가 완료됐어요.");
    }

    @Test
    @DisplayName("SMTP 인증 실패와 주소 형식 오류는 다시 보내도 같아서 재시도하지 않는다")
    void permanentFailures() {
        var authFailure = new MailAuthenticationException("인증 실패 parent@kidly.co.kr");
        willThrow(authFailure).given(javaMailSender).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> sender.send(EMAIL))
            .isInstanceOfSatisfying(EmailSendException.class, e -> {
                assertThat(e.isRetryable()).isFalse();
                assertThat(e).hasMessage("SMTP 인증 실패").hasCause(authFailure);
            });

        var parseFailure = new MailParseException("형식 오류 parent@kidly.co.kr");
        willThrow(parseFailure).given(javaMailSender).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> sender.send(EMAIL))
            .isInstanceOfSatisfying(EmailSendException.class, e -> {
                assertThat(e.isRetryable()).isFalse();
                assertThat(e).hasMessage("메일 주소 · 형식 오류").hasCause(parseFailure);
            });
    }

    @Test
    @DisplayName("연결 실패 같은 그 밖의 메일 오류는 재시도하고, 받는 사람 주소는 사유에 남기지 않는다")
    void retryableFailure() {
        var sendFailure = new MailSendException("550 5.1.1 <parent@kidly.co.kr> User unknown");
        willThrow(sendFailure).given(javaMailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> sender.send(EMAIL))
            .isInstanceOfSatisfying(EmailSendException.class, e -> {
                assertThat(e.isRetryable()).isTrue();
                assertThat(e).hasMessage("메일 발송 실패: MailSendException").hasCause(sendFailure);
                assertThat(e.getMessage()).doesNotContain("parent@kidly.co.kr");
            });
    }
}
