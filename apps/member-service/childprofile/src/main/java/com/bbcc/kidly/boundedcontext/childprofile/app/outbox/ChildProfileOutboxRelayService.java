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

/**
 * 아웃박스에 쌓인 메시지를 받은 순서대로 브로커에 보낸다.
 * 한 트랜잭션에서 릴레이 잠금을 먼저 얻는다. 서버가 여러 대여도 한 번에 하나의 릴레이만 돌아 메시지를 id 순서로 보낸다
 * (팀 Kafka 규칙). 잠금은 트랜잭션 잠금이라 보내기와 결과 기록을 같은 트랜잭션에서 하고, 커밋 한 번에 기록과 잠금 해제가 함께 된다.
 * 그래서 브로커가 멈추면 실패한 메시지 하나를 기다리는 동안(최대 약 15초) DB 연결 하나를 쥔다.
 * 보낸 뒤 커밋 전에 앱이 멈추거나 커밋이 실패하면, 이번 실행에서 보낸 메시지(최대 BATCH_SIZE건)를 다음 실행이 모두 다시 보낸다.
 * id는 커밋 직전에 저장한 순서라 동시에 도는 트랜잭션의 커밋 순서와 다를 수 있다. 받는 쪽은 occurredAt 비교로 중복 · 역순을 거른다.
 * kidly.kafka.producer.enabled가 false면 만들지 않는다(Kafka 없이 도는 테스트). 아웃박스 저장은 그때도 한다.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ChildProfileOutboxRelayService {

    // 한 번 실행에서 보내는 최대 건수. 남은 메시지는 다음 실행에서 보낸다
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
            if (!send(message)) {
                return;
            }
            message.markSent(LocalDateTime.now());
        }
    }

    // 보내지 못하면 실패를 기록하고 그 자리에서 멈춘다. 순서가 뒤집히지 않게 뒤 메시지는 보내지 않는다.
    // sent_at이 비어 있으므로 다음 실행의 findUnsent가 같은 메시지부터 다시 가져온다.
    // 예외 메시지에 eventType · eventId · 키 · 원인이 이미 있으므로 따로 반복하지 않고, 같은 장애가 반복되므로 스택 트레이스는 남기지 않는다.
    private boolean send(ChildProfileOutboxMessage message) {
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
