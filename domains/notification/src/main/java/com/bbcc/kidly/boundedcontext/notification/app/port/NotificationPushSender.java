package com.bbcc.kidly.boundedcontext.notification.app.port;

/**
 * 웹 푸시를 한 회원의 모든 FID에 한 번에 보내고 FID별 결과를 돌려준다.
 * 요청 전체가 실패하면 {@link PushSendException}을 던진다.
 */
public interface NotificationPushSender {

    PushSendResult send(PushMessage message);
}
