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
