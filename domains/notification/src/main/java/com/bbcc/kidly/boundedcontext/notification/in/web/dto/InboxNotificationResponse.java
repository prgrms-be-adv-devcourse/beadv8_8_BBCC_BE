package com.bbcc.kidly.boundedcontext.notification.in.web.dto;

import com.bbcc.kidly.boundedcontext.notification.app.dto.InboxNotificationView;
import java.time.LocalDateTime;

/**
 * 알림함 목록의 한 건. linkPath는 없으면 null.
 */
public record InboxNotificationResponse(
    Long notificationId,
    String type,
    String title,
    String body,
    String linkPath,
    boolean read,
    LocalDateTime createdAt
) {

    public static InboxNotificationResponse from(InboxNotificationView view) {
        return new InboxNotificationResponse(view.notificationId(), view.type(), view.title(), view.body(),
            view.linkPath(), view.read(), view.createdAt());
    }
}
