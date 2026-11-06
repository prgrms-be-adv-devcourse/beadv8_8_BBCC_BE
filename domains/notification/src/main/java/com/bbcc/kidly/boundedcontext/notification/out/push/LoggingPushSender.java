package com.bbcc.kidly.boundedcontext.notification.out.push;

import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationPushSender;
import com.bbcc.kidly.boundedcontext.notification.app.port.PushMessage;
import com.bbcc.kidly.boundedcontext.notification.app.port.PushSendResult;
import lombok.extern.slf4j.Slf4j;

/**
 * FCM 서비스 계정 키가 없는 환경(테스트 · 로컬 · 키 없는 서버)용. 보내지 않고 FID 수 · 제목만 남긴다.
 * 보낸 곳이 없다는 결과를 돌려주므로 알림은 SKIPPED가 된다.
 */
@Slf4j
public class LoggingPushSender implements NotificationPushSender {

    @Override
    public PushSendResult send(PushMessage message) {
        log.info("푸시 발송이 꺼져 있어 보내지 않습니다. fids={}, title={}", message.fids().size(), message.title());
        return PushSendResult.notSent();
    }
}
