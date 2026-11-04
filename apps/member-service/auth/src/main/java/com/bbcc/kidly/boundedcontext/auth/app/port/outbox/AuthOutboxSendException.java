package com.bbcc.kidly.boundedcontext.auth.app.port.outbox;

public class AuthOutboxSendException extends RuntimeException {
    public AuthOutboxSendException(String message) {
        super(message);
    }
}
