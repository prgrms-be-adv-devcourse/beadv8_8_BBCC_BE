package com.bbcc.kidly.boundedcontext.notification;

import com.bbcc.kidly.boundedcontext.notification.app.port.EmailSendException;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationEmailSender;
import com.bbcc.kidly.global.mail.Email;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 통합 · 기능 테스트에서 SMTP 대신 쓴다. 보낸 메일을 모아 두고, 정해 둔 주소로는 실패를 던진다.
 * 비동기 스레드에서도 불리므로 스레드 안전한 목록을 쓴다. `global.mail` 연동은 NotificationEmailSenderAdapterTest가 확인한다.
 */
@Primary
@Component
public class FakeNotificationEmailSender implements NotificationEmailSender {

    public static final String RETRYABLE_FAILURE = "retryable-failure@kidly.co.kr";
    public static final String PERMANENT_FAILURE = "permanent-failure@kidly.co.kr";

    private final List<Email> sent = new CopyOnWriteArrayList<>();

    public List<Email> sent() {
        return List.copyOf(sent);
    }

    public void reset() {
        sent.clear();
    }

    @Override
    public void send(Email email) {
        switch (email.to()) {
            case RETRYABLE_FAILURE -> throw new EmailSendException("SMTP 연결 실패", true, null);
            case PERMANENT_FAILURE -> throw new EmailSendException("SMTP 인증 실패", false, null);
            default -> sent.add(email);
        }
    }
}
