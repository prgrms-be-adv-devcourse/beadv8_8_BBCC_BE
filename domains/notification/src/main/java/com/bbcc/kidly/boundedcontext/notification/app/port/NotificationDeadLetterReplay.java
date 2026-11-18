package com.bbcc.kidly.boundedcontext.notification.app.port;

/**
 * 미지원 · 검증 실패 · 반영 실패는 예외이며 본문은 로그에 남기지 않는다.
 */
public interface NotificationDeadLetterReplay {

    void replay(NotificationDeadLetters.Message message);
}
