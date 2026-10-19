package com.bbcc.kidly.boundedcontext.notification.app.port;

import com.bbcc.kidly.global.mail.Email;

/**
 * 알림 이메일을 보낸다. 보내지 못하면 {@link EmailSendException}을 던진다.
 */
public interface NotificationEmailSender {

    void send(Email email);
}
