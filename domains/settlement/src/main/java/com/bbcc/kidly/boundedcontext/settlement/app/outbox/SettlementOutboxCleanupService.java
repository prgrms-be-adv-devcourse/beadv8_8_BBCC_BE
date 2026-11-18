package com.bbcc.kidly.boundedcontext.settlement.app.outbox;

import com.bbcc.kidly.boundedcontext.settlement.domain.outbox.SettlementOutboxRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB만 쓰므로 Kafka 보내기가 꺼져 있어도 있다.
 */
@Service
@RequiredArgsConstructor
public class SettlementOutboxCleanupService {

    private final SettlementOutboxRepository outboxRepository;
    private final SettlementOutboxProperties properties;

    @Transactional
    public int cleanUp() {
        return outboxRepository.deleteSentBefore(LocalDateTime.now().minus(properties.retention()));
    }
}
