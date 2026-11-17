package com.bbcc.kidly.boundedcontext.member.app.outbox;

import com.bbcc.kidly.boundedcontext.member.app.port.outbox.MemberOutboxSendException;
import com.bbcc.kidly.boundedcontext.member.app.port.outbox.MemberOutboxSender;
import com.bbcc.kidly.boundedcontext.member.domain.outbox.MemberOutboxMessage;
import com.bbcc.kidly.boundedcontext.member.domain.outbox.MemberOutboxRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 커밋된 아웃박스 메시지를 전송 스레드 하나가 바로 보내고, 보낸 것만 한 트랜잭션에서 한 번에 sent_at을 기록한다.
 * Kafka 응답을 기다리는 동안에는 트랜잭션을 열지 않아 DB 연결을 쥐지 않는다.
 * 대기열이 차거나, 보내지 못하거나, 앱이 내려가 남은 메시지는 행이 DB에 그대로 있으므로 5분 뒤 릴레이가 보낸다.
 * 그래서 실패 기록 · 재시도 · 종료 시 비우기를 하지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MemberOutboxDispatcher {

    private static final int QUEUE_CAPACITY = 10_000;
    private static final int BATCH_SIZE = 100;

    private final BlockingQueue<MemberOutboxMessage> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final MemberOutboxRepository repository;
    private final MemberOutboxSender sender;
    private final TransactionTemplate transactionTemplate;
    private Thread worker;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(MemberOutboxMessage message) {
        if (!queue.offer(message)) {
            log.warn("회원 아웃박스 대기열이 가득 차 릴레이에 맡깁니다. eventType={} eventId={} key={}",
                message.getEventType(), message.getEventId(), message.getMessageKey());
        }
    }

    @PostConstruct
    void start() {
        worker = Thread.ofPlatform().name("member-outbox-dispatcher").daemon().start(this::run);
    }

    @PreDestroy
    void stop() {
        worker.interrupt();
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                var batch = new ArrayList<MemberOutboxMessage>(BATCH_SIZE);
                batch.add(queue.take());
                queue.drainTo(batch, BATCH_SIZE - 1);
                sendBatch(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                log.warn("회원 아웃박스 바로 전송이 예외로 끝났습니다. error={}", e.getClass().getSimpleName());
            }
        }
    }

    // 보내지 못하면 그 묶음에서 멈춘다. 브로커가 멈췄을 때 묶음마다 한 번만 기다리게 한다
    public void sendBatch(List<MemberOutboxMessage> batch) {
        var sentIds = new ArrayList<Long>();
        for (var message : batch) {
            try {
                sender.send(message);
                sentIds.add(message.getId());
            } catch (MemberOutboxSendException e) {
                log.warn("회원 아웃박스 바로 전송에 실패해 이번 묶음을 멈춥니다. {}", e.getMessage());
                break;
            }
        }
        if (!sentIds.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> repository.markSent(sentIds, LocalDateTime.now()));
        }
    }
}
