package com.bbcc.kidly.global.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderTest {

    private static final String FROM = "kidly.team@gmail.com";

    @Mock
    private JavaMailSender javaMailSender;

    @Test
    @DisplayName("보내는 주소 · 받는 사람 · 제목 · 본문을 담아 SMTP로 보낸다")
    void send() {
        SmtpEmailSender sender = new SmtpEmailSender(javaMailSender, FROM);

        sender.send(new Email("parent@kidly.dev", "결제가 완료됐어요", "주문 #1 결제가 완료됐어요."));

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        then(javaMailSender).should().send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo(FROM);
        assertThat(message.getValue().getTo()).containsExactly("parent@kidly.dev");
        assertThat(message.getValue().getSubject()).isEqualTo("결제가 완료됐어요");
        assertThat(message.getValue().getText()).isEqualTo("주문 #1 결제가 완료됐어요.");
    }
}
