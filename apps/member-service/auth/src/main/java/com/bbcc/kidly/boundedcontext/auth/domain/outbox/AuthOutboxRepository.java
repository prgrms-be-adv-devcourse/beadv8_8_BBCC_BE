package com.bbcc.kidly.boundedcontext.auth.domain.outbox;

import java.time.LocalDateTime;
import java.util.List;

public interface AuthOutboxRepository {
    AuthOutboxMessage save(AuthOutboxMessage message);
    boolean tryLockRelay();
    List<AuthOutboxMessage> findUnsentCreatedBefore(LocalDateTime cutoff, int limit);
    void markSent(List<Long> ids, LocalDateTime now);
    int deleteSentBefore(LocalDateTime cutoff);
}
