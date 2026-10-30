package com.bbcc.kidly.boundedcontext.settlement.app.outbox;

import com.bbcc.kidly.boundedcontext.settlement.domain.outbox.SettlementOutboxRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보낸 정산 이벤트 아웃박스 메시지를 보관 기간 뒤 지운다. DB만 쓰므로 Kafka 보내기가 꺼져 있어도 있다.
 */
@Service
@RequiredArgsConstructor
public class SettlementOutboxCleanupService {

    private final SettlementOutboxRepository outboxRepository;
    private final SettlementOutboxProperties properties;

    /**
     * 보관 기간보다 먼저 보낸 메시지를 지운다. 안 보낸 메시지는 남긴다.
     *
     * @return 지운 건수
     */
    @Transactional
    public int cleanUp() {
        return outboxRepository.deleteSentBefore(LocalDateTime.now().minus(properties.retention()));
    }
}
