package com.bbcc.kidly.boundedcontext.notification.domain;

/**
 * 알림을 보내는 수단. EMAIL은 회원 이메일로, PUSH는 회원 브라우저의 모든 FCM 토큰으로 보낸다.
 */
public enum NotificationChannel {
    EMAIL,
    PUSH
}
