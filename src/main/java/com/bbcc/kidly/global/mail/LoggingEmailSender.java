package com.bbcc.kidly.global.mail;

import lombok.extern.slf4j.Slf4j;

/**
 * 메일 발송을 껐거나 SMTP 계정이 없는 환경(테스트 · 계정 없는 로컬 · 서버)용. 보내지 않고 받는 사람 · 제목만 남긴다 (본문은 개인 정보가 있을 수 있어 남기지 않는다)
 */
@Slf4j
class LoggingEmailSender implements EmailSender {

    @Override
    public void send(Email email) {
        log.info("메일 발송이 꺼져 있어 보내지 않습니다. to={}, subject={}", email.to(), email.subject());
    }
}
