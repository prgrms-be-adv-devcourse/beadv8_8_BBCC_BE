package com.bbcc.kidly.boundedcontext.notification.out.push;

import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationPushSender;
import com.bbcc.kidly.boundedcontext.notification.app.port.PushFailure;
import com.bbcc.kidly.boundedcontext.notification.app.port.PushMessage;
import com.bbcc.kidly.boundedcontext.notification.app.port.PushSendException;
import com.bbcc.kidly.boundedcontext.notification.app.port.PushSendResult;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushFcmOptions;
import com.google.firebase.messaging.WebpushNotification;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;

/**
 * 웹 푸시를 FCM HTTP v1(Firebase Admin SDK)로 한 회원의 모든 FID에 한 번에 보낸다(sendEachForMulticast).
 * 대상은 등록 토큰이 아닌 FID(Firebase Installation ID)다. 등록 토큰 API는 Admin SDK 9.10.0부터 deprecated다.
 * FCM 오류 코드는 PushFailure로 묶는다: UNREGISTERED · INVALID_ARGUMENT는 FID 정리, UNAVAILABLE · INTERNAL ·
 * QUOTA_EXCEEDED는 재시도, 그 밖(발신자 불일치 · 웹 푸시 인증 오류 · 서비스 계정 권한)은 재시도하지 않는다.
 * 예외 메시지에는 FID · 키를 넣지 않는다. 앱이 내려갈 때 Firebase 앱을 지운다(close).
 */
public class FcmPushSender implements NotificationPushSender, AutoCloseable {

    // 기본 Firebase 앱과 겹치지 않게 이름을 붙인다
    private static final String APP_NAME = "kidly-notification";

    private final FirebaseApp firebaseApp;
    private final FirebaseMessaging firebaseMessaging;

    private FcmPushSender(FirebaseApp firebaseApp) {
        this(firebaseApp, FirebaseMessaging.getInstance(firebaseApp));
    }

    FcmPushSender(FirebaseApp firebaseApp, FirebaseMessaging firebaseMessaging) {
        this.firebaseApp = firebaseApp;
        this.firebaseMessaging = firebaseMessaging;
    }

    /**
     * Base64로 감싼 서비스 계정 키 JSON으로 만든다. 읽지 못하면 키 내용 없이 실패한다(설정 오류라 기동을 멈춘다).
     */
    public static FcmPushSender fromBase64ServiceAccount(String base64) {
        byte[] json;
        try {
            // GNU base64처럼 줄바꿈이 들어간 값도 읽는다
            json = Base64.getMimeDecoder().decode(base64.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("FIREBASE_CREDENTIALS가 Base64 값이 아닙니다");
        }
        ServiceAccountCredentials credentials;
        try (var in = new ByteArrayInputStream(json)) {
            credentials = ServiceAccountCredentials.fromStream(in);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("FIREBASE_CREDENTIALS를 서비스 계정 키 JSON으로 읽지 못했습니다");
        }
        var options = FirebaseOptions.builder().setCredentials(credentials).build();
        return new FcmPushSender(FirebaseApp.initializeApp(options, APP_NAME));
    }

    @Override
    public PushSendResult send(PushMessage message) {
        var multicast = MulticastMessage.builder()
            .addAllFids(message.fids())
            .setWebpushConfig(webpush(message))
            .build();
        BatchResponse response;
        try {
            response = firebaseMessaging.sendEachForMulticast(multicast);
        } catch (FirebaseMessagingException e) {
            var failure = classify(e.getMessagingErrorCode(), e.getErrorCode());
            throw new PushSendException("FCM 요청 실패: " + failure, failure == PushFailure.RETRYABLE, e);
        } catch (RuntimeException e) {
            // SDK 안의 예상 못 한 오류(네트워크 · 직렬화 등). 알림이 SENDING에 멈추지 않게 일시 오류로 다시 시도한다
            throw new PushSendException("FCM 요청 실패: " + e.getClass().getSimpleName(), true, e);
        }
        var failures = new ArrayList<PushSendResult.Failure>();
        var responses = response.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            var each = responses.get(i);
            if (!each.isSuccessful()) {
                var exception = each.getException();
                failures.add(new PushSendResult.Failure(message.fids().get(i),
                    classify(exception.getMessagingErrorCode(), exception.getErrorCode())));
            }
        }
        return new PushSendResult(response.getSuccessCount(), failures);
    }

    // FCM은 누르면 열 주소로 HTTPS만 받는다. 로컬(http) 주소는 넣지 않는다(누르면 아무 데도 가지 않는다)
    private static WebpushConfig webpush(PushMessage message) {
        var webpush = WebpushConfig.builder()
            .setNotification(new WebpushNotification(message.title(), message.body()));
        if (message.link().startsWith("https://")) {
            webpush.setFcmOptions(WebpushFcmOptions.withLink(message.link()));
        }
        return webpush.build();
    }

    /**
     * FCM 오류를 Kidly가 다루는 방식으로 묶는다. FCM 고유 코드가 없으면(네트워크 오류 등) 공통 코드로 정한다.
     */
    static PushFailure classify(MessagingErrorCode messagingErrorCode, ErrorCode errorCode) {
        if (messagingErrorCode != null) {
            return switch (messagingErrorCode) {
                case UNREGISTERED -> PushFailure.UNREGISTERED;
                case INVALID_ARGUMENT -> PushFailure.INVALID_ARGUMENT;
                case UNAVAILABLE, INTERNAL, QUOTA_EXCEEDED -> PushFailure.RETRYABLE;
                case SENDER_ID_MISMATCH, THIRD_PARTY_AUTH_ERROR -> PushFailure.REJECTED;
            };
        }
        if (errorCode == null) {
            return PushFailure.RETRYABLE;
        }
        return switch (errorCode) {
            case NOT_FOUND -> PushFailure.UNREGISTERED;
            case INVALID_ARGUMENT -> PushFailure.INVALID_ARGUMENT;
            case UNAVAILABLE, INTERNAL, DEADLINE_EXCEEDED, RESOURCE_EXHAUSTED, ABORTED, CANCELLED, UNKNOWN ->
                PushFailure.RETRYABLE;
            default -> PushFailure.REJECTED;
        };
    }

    @Override
    public void close() {
        firebaseApp.delete();
    }
}
