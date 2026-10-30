package com.bbcc.kidly.boundedcontext.product.app.port.outbox;

/**
 * 브로커가 아웃박스 메시지를 받았는지 확인하지 못했다. 보내기 전에 실패했거나, 실패 응답을 받았거나, 응답이 시간 안에 오지 않은 경우다.
 * 메시지에는 eventType · eventId · 키와 원인만 담고 본문은 담지 않는다.
 */
public class ProductEventSendException extends RuntimeException {

    public ProductEventSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
