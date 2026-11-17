package com.bbcc.kidly.boundedcontext.member.app.outbox;

import com.bbcc.kidly.boundedcontext.member.app.port.outbox.MemberOutboxSendException;
import com.bbcc.kidly.boundedcontext.member.app.port.outbox.MemberOutboxSender;
import com.bbcc.kidly.boundedcontext.member.domain.outbox.MemberOutboxMessage;
import com.bbcc.kidly.boundedcontext.member.domain.outbox.MemberOutboxRepository;
import java.time.Duration;
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
public class MemberOutboxRelayService {

    private static final int BATCH_SIZE = 100;
    private static final Duration IMMEDIATE_SEND_GRACE = Duration.ofMinutes(5);

    private final MemberOutboxRepository outboxRepository;
    private final MemberOutboxSender sender;

    @Transactional
    public void relay() {
        if (!outboxRepository.tryLockRelay()) {
            return;
        }
        var messages = outboxRepository.findUnsentCreatedBefore(
            LocalDateTime.now().minus(IMMEDIATE_SEND_GRACE), BATCH_SIZE);
        for (var message : messages) {
            if (!send(message)) {
                return;
            }
            message.markSent(LocalDateTime.now());
        }
    }

    private boolean send(MemberOutboxMessage message) {
        try {
            sender.send(message);
            return true;
        } catch (MemberOutboxSendException e) {
            message.recordFailure(e.getMessage());
            log.warn("회원 아웃박스 전송 실패로 이번 릴레이를 멈춥니다. attempt={} {}",
                message.getAttemptCount(), e.getMessage());
            return false;
        }
    }
}
