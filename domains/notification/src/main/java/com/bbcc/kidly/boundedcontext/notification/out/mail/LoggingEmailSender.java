package com.bbcc.kidly.boundedcontext.notification.out.mail;

import com.bbcc.kidly.boundedcontext.notification.app.port.Email;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationEmailSender;
import lombok.extern.slf4j.Slf4j;

/**
 * 메일 발송을 껐거나 SMTP 계정이 없는 환경(테스트 · 계정 없는 로컬 · 서버)용. 보내지 않고 제목만 남긴다 (받는 주소 · 본문은 개인 정보라 남기지 않는다)
 */
@Slf4j
public class LoggingEmailSender implements NotificationEmailSender {

    @Override
    public void send(Email email) {
        log.info("메일 발송이 꺼져 있어 보내지 않습니다. subject={}", email.subject());
    }
}
