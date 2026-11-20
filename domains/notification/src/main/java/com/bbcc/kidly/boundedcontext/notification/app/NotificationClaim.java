package com.bbcc.kidly.boundedcontext.notification.app;

import java.time.LocalDateTime;

/**
 * 재시도 스케줄러가 SENDING으로 맡은 알림과 맡은 시각. 발송할 때 행의 맡은 시각이 이 값과 같아야 보낸다.
 */
public record NotificationClaim(Long notificationId, LocalDateTime claimedAt) {
}
