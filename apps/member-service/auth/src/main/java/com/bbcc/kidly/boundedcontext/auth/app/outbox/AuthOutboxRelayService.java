package com.bbcc.kidly.boundedcontext.auth.app.outbox;

import com.bbcc.kidly.boundedcontext.auth.app.port.outbox.AuthOutboxSender;
import com.bbcc.kidly.boundedcontext.auth.app.port.outbox.AuthOutboxSendException;
import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
public class AuthOutboxRelayService {
    // 한 번 실행에서 보내는 최대 건수. 남은 메시지는 다음 실행에서 보낸다
    private static final int BATCH_SIZE = 100;

    private final AuthOutboxRepository repository;
    private final AuthOutboxSender sender;

    @Transactional
    public void relay() {
        if (!repository.tryLockRelay()) {
            return;
        }
        for (var message : repository.findOldest(BATCH_SIZE)) {
            try {
                sender.send(message);
            } catch (AuthOutboxSendException e) {
                log.warn("인증 이벤트 전송 실패 {}", e.getMessage());
                return;
            }
            repository.delete(message);
        }
    }
}
