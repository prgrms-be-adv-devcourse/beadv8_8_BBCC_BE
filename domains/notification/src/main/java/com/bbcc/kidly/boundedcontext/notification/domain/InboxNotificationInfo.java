package com.bbcc.kidly.boundedcontext.notification.domain;

public record InboxNotificationInfo(
    InboxRecipient recipient,
    NotificationType type,
    String title,
    String body,
    String linkPath,
    String dedupKey
) {
}
