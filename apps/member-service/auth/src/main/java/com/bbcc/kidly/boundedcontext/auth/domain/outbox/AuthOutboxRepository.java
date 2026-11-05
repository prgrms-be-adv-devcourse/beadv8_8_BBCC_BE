package com.bbcc.kidly.boundedcontext.auth.domain.outbox;

import java.util.List;

public interface AuthOutboxRepository {
    AuthOutboxMessage save(AuthOutboxMessage message);
    boolean tryLockRelay();
    List<AuthOutboxMessage> findOldest(int limit);
    void delete(AuthOutboxMessage message);
}
