package com.bbcc.kidly.global.mail;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * SMTP(spring.mail, Gmail 공용 계정)로 보낸다. 보내는 주소는 kidly.mail.from이다
 */
class SmtpEmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;
    private final String from;

    SmtpEmailSender(JavaMailSender javaMailSender, String from) {
        this.javaMailSender = javaMailSender;
        this.from = from;
    }

    @Override
    public void send(Email email) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email.to());
        message.setSubject(email.subject());
        message.setText(email.text());
        javaMailSender.send(message);
    }
}
