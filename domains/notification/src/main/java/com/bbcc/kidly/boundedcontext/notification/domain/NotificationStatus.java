package com.bbcc.kidly.boundedcontext.notification.domain;

/**
 * PENDING → SENDING(발송을 맡음) → SENT / FAILED / SKIPPED(보낼 주소 없음).
 * FAILED는 다음 시도 시각이 있으면 그 시각에 다시 맡을 수 있다.
 */
public enum NotificationStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED,
    SKIPPED
}
