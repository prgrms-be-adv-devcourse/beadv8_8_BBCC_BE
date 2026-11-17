package com.bbcc.kidly.boundedcontext.seller.app.outbox;

import com.bbcc.kidly.boundedcontext.seller.app.port.outbox.SellerOutboxSendException;
import com.bbcc.kidly.boundedcontext.seller.app.port.outbox.SellerOutboxSender;
import com.bbcc.kidly.boundedcontext.seller.domain.outbox.SellerOutboxMessage;
import com.bbcc.kidly.boundedcontext.seller.domain.outbox.SellerOutboxRepository;
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
public class SellerOutboxRelayService {

    private static final int BATCH_SIZE = 100;
    private static final Duration IMMEDIATE_SEND_GRACE = Duration.ofMinutes(5);

    private final SellerOutboxRepository outboxRepository;
    private final SellerOutboxSender sender;

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

    private boolean send(SellerOutboxMessage message) {
        try {
            sender.send(message);
            return true;
        } catch (SellerOutboxSendException e) {
            message.recordFailure(e.getMessage());
            log.warn("판매자 아웃박스 전송 실패로 이번 릴레이를 멈춥니다. attempt={} {}",
                message.getAttemptCount(), e.getMessage());
            return false;
        }
    }
}
