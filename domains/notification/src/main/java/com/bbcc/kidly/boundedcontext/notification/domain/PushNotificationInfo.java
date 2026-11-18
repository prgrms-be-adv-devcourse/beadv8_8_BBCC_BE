package com.bbcc.kidly.boundedcontext.notification.domain;

import java.util.UUID;

/**
 * @param eventId 같은 일로 푸시를 한 건만 만들기 위한 키. 같은 이벤트로 같은 채널의 알림은 한 건만 만든다
 * @param linkPath 누르면 이동할 사이트 안 경로. 없으면 null(사이트 첫 화면)
 */
public record PushNotificationInfo(UUID eventId, Long memberId, NotificationType type, String title, String body,
                                   String linkPath) {
}
