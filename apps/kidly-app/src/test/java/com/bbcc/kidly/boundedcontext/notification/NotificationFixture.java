package com.bbcc.kidly.boundedcontext.notification;

import com.bbcc.kidly.boundedcontext.notification.domain.EmailNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import java.time.LocalDateTime;
import java.util.UUID;

public final class NotificationFixture {

    public static final Long MEMBER_ID = 1L;
    public static final String EMAIL = "parent@kidly.co.kr";
    public static final String TITLE = "[Kidly] 가입을 환영합니다";
    public static final String BODY = "Kidly에 오신 것을 환영합니다.";

    private NotificationFixture() {
    }

    public static Notification welcomeEmail(String recipient) {
        return Notification.email(new EmailNotificationInfo(
            UUID.randomUUID(), MEMBER_ID, NotificationType.WELCOME, recipient, TITLE, BODY));
    }

    // 발송을 맡은 상태
    public static Notification sending(String recipient, LocalDateTime startedAt) {
        var notification = welcomeEmail(recipient);
        notification.startSending(startedAt);
        return notification;
    }

    // 재시도할 수 있게 실패한 상태. 다음 시도 시각은 failedAt + 30초
    public static Notification retryableFailed(String recipient, LocalDateTime failedAt) {
        var notification = sending(recipient, failedAt);
        notification.markFailed("연결 실패", true, failedAt);
        return notification;
    }
}
