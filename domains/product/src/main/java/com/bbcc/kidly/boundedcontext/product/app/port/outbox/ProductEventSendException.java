package com.bbcc.kidly.boundedcontext.product.app.port.outbox;

/**
 * 아웃박스 메시지를 브로커에 보내지 못했다. 메시지에는 원인만 담고 본문은 담지 않는다.
 */
public class ProductEventSendException extends RuntimeException {

    public ProductEventSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
