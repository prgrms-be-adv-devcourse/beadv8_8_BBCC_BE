package com.bbcc.kidly.boundedcontext.notification.app.port;

/**
 * FID 하나로 푸시를 보내지 못한 이유. FCM 오류 코드를 Kidly가 다루는 방식대로 묶었다.
 */
public enum PushFailure {
    // 브라우저가 구독을 끊었거나 FID가 만료됐다. FID를 지운다
    UNREGISTERED,
    // FID가 잘못됐거나 메시지가 잘못됐다. FID를 지운다(모든 FID가 이 오류면 메시지 문제로 보고 지우지 않는다)
    INVALID_ARGUMENT,
    // FCM 일시 오류 · 한도 초과. 같은 간격으로 다시 시도한다
    RETRYABLE,
    // 그 밖의 거절(발신자 불일치 · 웹 푸시 인증 오류 등). 다시 보내도 같아서 다시 시도하지 않는다
    REJECTED
}
