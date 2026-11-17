package com.bbcc.kidly.boundedcontext.product.app.outbox;

import com.bbcc.kidly.boundedcontext.product.app.port.outbox.ProductEventSendException;
import com.bbcc.kidly.boundedcontext.product.app.port.outbox.ProductEventSender;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductOutboxRelayService {

    private final ProductOutboxRepository outboxRepository;
    private final ProductEventSender sender;
    private final ProductOutboxProperties properties;

    public void relay() {
        List<ProductOutboxMessage> messages = outboxRepository.findUnsent(properties.batchSize());
        int sent = 0;
        try {
            for (ProductOutboxMessage message : messages) {
                if (!send(message)) {
                    break;
                }
                message.markSent(LocalDateTime.now());
                outboxRepository.save(message);
                sent++;
            }
        } finally {
            if (sent > 0) {
                log.info("상품 아웃박스 메시지를 보냈습니다. sent={}", sent);
            }
        }
    }

    private boolean send(ProductOutboxMessage message) {
        try {
            sender.send(message);
            return true;
        } catch (ProductEventSendException e) {
            message.recordFailure(e.getMessage());
            log.error("상품 아웃박스 전송 실패로 이번 릴레이를 멈춥니다. attempt={} {}", message.getAttemptCount(), e.getMessage());
            outboxRepository.save(message);
            return false;
        }
    }
}
