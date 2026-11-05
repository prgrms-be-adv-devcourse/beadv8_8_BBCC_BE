package com.bbcc.kidly.boundedcontext.notification.app.dto;

import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotification;
import java.time.LocalDateTime;

/**
 * 알림함 목록의 한 건. type은 알림 유형 이름이다.
 */
public record InboxNotificationView(
    Long notificationId,
    String type,
    String title,
    String body,
    String linkPath,
    boolean read,
    LocalDateTime createdAt
) {

    public static InboxNotificationView from(InboxNotification notification) {
        return new InboxNotificationView(notification.getId(), notification.getType().name(),
            notification.getTitle(), notification.getBody(), notification.getLinkPath(), notification.isRead(),
            notification.getCreatedAt());
    }
}
