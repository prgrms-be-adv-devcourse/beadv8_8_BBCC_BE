package com.bbcc.kidly.boundedcontext.product.app.outbox;

import com.bbcc.kidly.boundedcontext.product.app.port.outbox.ProductEventSender;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 아웃박스에 쌓인 메시지를 받은 순서대로 브로커에 보낸다.
 * relay()에는 트랜잭션을 걸지 않는다. 브로커가 멈추면 메시지 하나에 최대 약 17초를 기다리는데, 그동안 DB 연결을 쥐고 있지 않기 위해서다.
 * 보낸 결과는 메시지마다 save로 저장하고, Spring Data의 save가 자기 트랜잭션을 열어 그 메시지의 결과만 바로 커밋한다.
 */
@Service
@RequiredArgsConstructor
public class ProductOutboxRelayService {

    private final ProductOutboxRepository outboxRepository;
    private final ProductEventSender sender;
    private final ProductOutboxProperties properties;

    public void relay() {
        List<ProductOutboxMessage> messages = outboxRepository.findUnsent(properties.batchSize());
        for (ProductOutboxMessage message : messages) {
            sender.send(message);
            message.markSent(LocalDateTime.now());
            outboxRepository.save(message);
        }
    }
}
