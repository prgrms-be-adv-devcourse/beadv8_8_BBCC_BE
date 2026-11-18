package com.bbcc.kidly.boundedcontext.notification.app.port;

/**
 * 보내지 못하면 {@link EmailSendException}을 던진다.
 */
public interface NotificationEmailSender {

    void send(Email email);
}
