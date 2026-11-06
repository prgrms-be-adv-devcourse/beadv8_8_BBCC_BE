package com.bbcc.kidly.boundedcontext.notification;

import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationPushSender;
import com.bbcc.kidly.boundedcontext.notification.app.port.PushFailure;
import com.bbcc.kidly.boundedcontext.notification.app.port.PushMessage;
import com.bbcc.kidly.boundedcontext.notification.app.port.PushSendResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 통합 · 기능 테스트에서 FCM 대신 쓴다. 보낸 푸시를 모아 두고, FID 접두어로 FCM 오류를 흉내 낸다.
 * 비동기 스레드에서도 불리므로 스레드 안전한 목록을 쓴다. FCM 오류 분류는 FcmPushSenderTest가 확인한다.
 */
@Primary
@Component
public class FakeNotificationPushSender implements NotificationPushSender {

    public static final String UNREGISTERED = "unregistered-";
    public static final String INVALID = "invalid-";
    public static final String BUSY = "busy-";

    private final List<PushMessage> sent = new CopyOnWriteArrayList<>();

    public List<PushMessage> sent() {
        return List.copyOf(sent);
    }

    public void reset() {
        sent.clear();
    }

    @Override
    public PushSendResult send(PushMessage message) {
        sent.add(message);
        var failures = new ArrayList<PushSendResult.Failure>();
        for (var fid : message.fids()) {
            if (fid.startsWith(UNREGISTERED)) {
                failures.add(new PushSendResult.Failure(fid, PushFailure.UNREGISTERED));
            } else if (fid.startsWith(INVALID)) {
                failures.add(new PushSendResult.Failure(fid, PushFailure.INVALID_ARGUMENT));
            } else if (fid.startsWith(BUSY)) {
                failures.add(new PushSendResult.Failure(fid, PushFailure.RETRYABLE));
            }
        }
        return new PushSendResult(message.fids().size() - failures.size(), failures);
    }
}
