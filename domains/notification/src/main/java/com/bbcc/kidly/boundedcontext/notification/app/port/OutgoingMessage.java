package com.bbcc.kidly.boundedcontext.notification.app.port;

/**
 * 발송을 맡은 알림 한 건을 채널의 발송기로 보낼 내용. 채널마다 하나다.
 */
public sealed interface OutgoingMessage permits Email, PushMessage {
}
