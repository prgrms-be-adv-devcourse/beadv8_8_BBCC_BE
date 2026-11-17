package com.bbcc.kidly.boundedcontext.product.app.port.outbox;

public class ProductEventSendException extends RuntimeException {

    public ProductEventSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
