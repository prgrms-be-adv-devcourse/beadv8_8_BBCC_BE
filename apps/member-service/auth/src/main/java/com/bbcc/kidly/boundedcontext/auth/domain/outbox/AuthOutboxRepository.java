package com.bbcc.kidly.boundedcontext.auth.domain.outbox;

import com.bbcc.kidly.member.support.outbox.OutboxRepository;

public interface AuthOutboxRepository extends OutboxRepository<AuthOutboxMessage> {
}
