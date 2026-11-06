package com.bbcc.kidly.boundedcontext.notification.app.port;

import lombok.Getter;

/**
 * 푸시 요청 전체가 실패했다(인증 실패 · FCM 연결 실패). FID별 실패는 {@link PushSendResult}로 돌려준다.
 * {@code retryable}이 false면 다시 보내도 같은 결과다. 메시지에는 FID · 키를 넣지 않는다.
 */
@Getter
public class PushSendException extends RuntimeException {

    private final boolean retryable;

    public PushSendException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }
}
