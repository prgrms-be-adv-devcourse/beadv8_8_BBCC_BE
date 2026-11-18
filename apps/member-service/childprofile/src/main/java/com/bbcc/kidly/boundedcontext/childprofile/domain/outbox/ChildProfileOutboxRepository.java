package com.bbcc.kidly.boundedcontext.childprofile.domain.outbox;

import com.bbcc.kidly.member.support.outbox.OutboxRepository;
import java.util.UUID;

public interface ChildProfileOutboxRepository extends OutboxRepository<ChildProfileOutboxMessage> {

    boolean existsByEventId(UUID eventId);
}
