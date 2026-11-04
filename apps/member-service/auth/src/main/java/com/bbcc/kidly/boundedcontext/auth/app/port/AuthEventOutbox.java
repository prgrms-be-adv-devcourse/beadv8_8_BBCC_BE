package com.bbcc.kidly.boundedcontext.auth.app.port;

import com.bbcc.kidly.shared.event.DomainEvent;

public interface AuthEventOutbox {
    void append(DomainEvent event);
}
