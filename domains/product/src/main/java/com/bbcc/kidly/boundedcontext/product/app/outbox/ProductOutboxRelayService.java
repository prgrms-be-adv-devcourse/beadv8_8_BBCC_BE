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

/**
 * 아웃박스에 쌓인 메시지를 받은 순서대로 브로커에 보낸다.
 * relay()에는 트랜잭션을 걸지 않는다. 브로커가 멈추면 메시지 하나에 최대 약 17초를 기다리는데, 그동안 DB 연결을 쥐고 있지 않기 위해서다.
 * 보낸 결과는 메시지마다 save로 저장하고, Spring Data의 save가 자기 트랜잭션을 열어 그 메시지의 결과만 바로 커밋한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductOutboxRelayService {

    private final ProductOutboxRepository outboxRepository;
    private final ProductEventSender sender;
    private final ProductOutboxProperties properties;

    public void relay() {
        List<ProductOutboxMessage> messages = outboxRepository.findUnsent(properties.batchSize());
        for (ProductOutboxMessage message : messages) {
            if (!send(message)) {
                return;
            }
            message.markSent(LocalDateTime.now());
            outboxRepository.save(message);
        }
    }

    // 보내지 못하면 실패를 기록하고 그 자리에서 멈춘다. 순서가 뒤집히지 않게 뒤 메시지는 보내지 않는다.
    // sent_at이 비어 있으므로 다음 실행의 findUnsent가 같은 메시지부터 다시 가져온다.
    // 예외 메시지에 eventType · eventId · 키 · 원인이 이미 있으므로 따로 반복하지 않고, 같은 장애가 반복되므로 스택 트레이스는 남기지 않는다.
    // 전송이 멈추면 뒤의 상품 이벤트가 모두 멈추므로 ERROR로 남긴다. 운영 알림(Slack)은 ERROR 건수로 온다.
    private boolean send(ProductOutboxMessage message) {
        try {
            sender.send(message);
            return true;
        } catch (ProductEventSendException e) {
            message.recordFailure(e.getMessage());
            outboxRepository.save(message);
            log.error("상품 아웃박스 전송 실패로 이번 릴레이를 멈춥니다. attempt={} {}", message.getAttemptCount(), e.getMessage());
            return false;
        }
    }
}
