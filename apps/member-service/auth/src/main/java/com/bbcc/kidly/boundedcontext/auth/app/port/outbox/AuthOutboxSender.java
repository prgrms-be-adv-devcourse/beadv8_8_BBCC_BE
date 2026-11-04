package com.bbcc.kidly.boundedcontext.auth.app.port.outbox;

import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxMessage;

public interface AuthOutboxSender {
    void send(AuthOutboxMessage message);
}
