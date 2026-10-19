package com.bbcc.kidly.boundedcontext.notification.out.mail;

import com.bbcc.kidly.boundedcontext.notification.app.port.EmailSendException;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationEmailSender;
import com.bbcc.kidly.global.mail.Email;
import com.bbcc.kidly.global.mail.EmailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.stereotype.Component;

/**
 * 알림 메일을 global.mail 발송기(Gmail SMTP · 계정이 없으면 로그)로 보낸다.
 * 인증 실패 · 주소 형식 오류는 다시 보내도 같아서 재시도하지 않고, 그 밖의 메일 오류(연결 실패 · 일시 오류 · 한도 초과)는 재시도한다.
 * SMTP 오류 메시지에는 받는 사람 주소가 들어 있을 수 있어 그대로 넘기지 않고 고정된 사유만 담는다(DB · 로그에 주소를 남기지 않는다).
 */
@Component
@RequiredArgsConstructor
public class NotificationEmailSenderAdapter implements NotificationEmailSender {

    private final EmailSender emailSender;

    @Override
    public void send(Email email) {
        try {
            emailSender.send(email);
        } catch (MailAuthenticationException e) {
            throw new EmailSendException("SMTP 인증 실패", false, e);
        } catch (MailParseException e) {
            throw new EmailSendException("메일 주소 · 형식 오류", false, e);
        } catch (MailException e) {
            throw new EmailSendException("메일 발송 실패: " + e.getClass().getSimpleName(), true, e);
        }
    }
}
