package com.bbcc.kidly.boundedcontext.notification.app.port;

import lombok.Getter;

/**
 * 이메일을 보내지 못했다. {@code retryable}이 false면 다시 보내도 같은 결과다(SMTP 인증 실패, 주소 형식 오류).
 */
@Getter
public class EmailSendException extends RuntimeException {

    private final boolean retryable;

    public EmailSendException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }
}
