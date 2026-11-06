package com.bbcc.kidly.boundedcontext.childprofile.app.outbox;

import com.bbcc.kidly.boundedcontext.childprofile.app.port.outbox.ChildProfileEventSendException;
import com.bbcc.kidly.boundedcontext.childprofile.app.port.outbox.ChildProfileEventSender;
import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxMessage;
import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ChildProfileOutboxRelayService {

    private static final int BATCH_SIZE = 100;

    private final ChildProfileOutboxRepository outboxRepository;
    private final ChildProfileEventSender sender;

    @Transactional
    public void relay() {
        if (!outboxRepository.tryLockRelay()) {
            return;
        }
        var messages = outboxRepository.findUnsent(BATCH_SIZE);
        for (var message : messages) {
            if (!sendOrRecordFailure(message)) {
                return;
            }
            message.markSent(LocalDateTime.now());
        }
    }

    private boolean sendOrRecordFailure(ChildProfileOutboxMessage message) {
        try {
            sender.send(message);
            return true;
        } catch (ChildProfileEventSendException e) {
            message.recordFailure(e.getMessage());
            log.warn("자녀 프로필 아웃박스 전송 실패로 이번 릴레이를 멈춥니다. attempt={} {}",
                message.getAttemptCount(), e.getMessage());
            return false;
        }
    }
}
