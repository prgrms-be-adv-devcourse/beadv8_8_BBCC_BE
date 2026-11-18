package com.bbcc.kidly.boundedcontext.notification.domain;

import java.util.UUID;

/**
 * @param eventId 알림을 만든 도메인 이벤트의 ID. 같은 이벤트로 같은 채널의 알림은 한 건만 만든다
 * @param recipient 받는 이메일 주소. 없으면 null(발송할 때 SKIPPED)
 */
public record EmailNotificationInfo(UUID eventId, Long memberId, NotificationType type, String recipient,
                                    String title, String body) {
}
