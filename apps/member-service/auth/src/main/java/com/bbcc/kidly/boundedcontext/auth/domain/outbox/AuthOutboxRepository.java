package com.bbcc.kidly.boundedcontext.auth.domain.outbox;

import java.time.LocalDateTime;
import java.util.List;

public interface AuthOutboxRepository {
    AuthOutboxMessage save(AuthOutboxMessage message);
    boolean tryLockRelay();
    List<AuthOutboxMessage> findCreatedBefore(LocalDateTime cutoff, int limit);
    void deleteAllByIdInBatch(Iterable<Long> ids);
    void delete(AuthOutboxMessage message);
}
