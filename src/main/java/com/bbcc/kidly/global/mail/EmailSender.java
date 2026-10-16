package com.bbcc.kidly.global.mail;

/**
 * 메일 발송. 도메인은 이 인터페이스로만 메일을 보낸다 (SMTP · 로그 중 무엇으로 보내는지는 설정이 정한다).
 * 요청 스레드에서 바로 보내므로, 업무 트랜잭션 안이 아니라 커밋 뒤(이벤트 리스너 등)에서 부른다.
 * 보내지 못하면 {@link org.springframework.mail.MailException}을 던진다
 */
public interface EmailSender {

    void send(Email email);
}
