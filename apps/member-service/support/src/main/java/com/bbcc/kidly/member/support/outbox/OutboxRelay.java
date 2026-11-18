package com.bbcc.kidly.member.support.outbox;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 아웃박스 메시지를 브로커로 보낸다. 커밋 직후에는 대기열과 작업 스레드로 바로 보내고,
 * 바로 보내지 못한 메시지는 주기 릴레이가 유예 시간 뒤에 보낸다. 보낸 지 보관 기간이 지난 행은 지운다.
 *
 * <p>도메인 모듈의 빈이 상속해 자기 리포지토리 · 전송기를 넘기고, 커밋 뒤 리스너를 자기 엔티티 타입으로 선언해 {@link #enqueue}를 부른다.
 * Spring은 제네릭 리스너의 타입 인자를 하위 클래스 기준으로 풀지 않아, 리스너를 여기에 두면 다른 모듈의 메시지까지 받는다.
 */
public abstract class OutboxRelay<T extends OutboxMessage> implements InitializingBean, DisposableBean {

    private static final int QUEUE_CAPACITY = 10_000;
    private static final int BATCH_SIZE = 100;
    private static final Duration IMMEDIATE_SEND_GRACE = Duration.ofMinutes(5);
    private static final Duration RETENTION = Duration.ofDays(7);

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final BlockingQueue<T> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final String domain;
    private final String workerName;
    private final OutboxRepository<T> repository;
    private final OutboxSender sender;
    private final TransactionTemplate transactionTemplate;
    private Thread worker;

    protected OutboxRelay(String domain, String workerName, OutboxRepository<T> repository, OutboxSender sender,
        TransactionTemplate transactionTemplate) {
        this.domain = domain;
        this.workerName = workerName;
        this.repository = repository;
        this.sender = sender;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional
    public void relay() {
        if (!repository.tryLockRelay()) {
            return;
        }
        var messages = repository.findUnsentCreatedBefore(
            LocalDateTime.now().minus(IMMEDIATE_SEND_GRACE), BATCH_SIZE);
        for (var message : messages) {
            if (!send(message)) {
                return;
            }
            message.markSent(LocalDateTime.now());
        }
    }

    // deleteSentBefore는 벌크 DELETE라 트랜잭션 안에서만 돈다. 리포지토리에는 트랜잭션을 붙이지 않으므로 여기서 경계를 정한다.
    @Transactional
    public void cleanUp() {
        var deleted = repository.deleteSentBefore(LocalDateTime.now().minus(RETENTION));
        if (deleted > 0) {
            log.info("보낸 {} 아웃박스 메시지를 지웠습니다. count={}", domain, deleted);
        }
    }

    protected void enqueue(T message) {
        if (!queue.offer(message)) {
            log.warn("{} 아웃박스 대기열이 가득 차 릴레이에 맡깁니다. eventType={} eventId={} key={}",
                domain, message.getEventType(), message.getEventId(), message.getMessageKey());
        }
    }

    public void sendBatch(List<T> batch) {
        var sentIds = new ArrayList<Long>();
        for (var message : batch) {
            try {
                sender.send(message);
                sentIds.add(message.getId());
            } catch (OutboxSendException e) {
                log.warn("{} 아웃박스 바로 전송에 실패해 이번 묶음을 멈춥니다. {}", domain, e.getMessage());
                break;
            }
        }
        if (!sentIds.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> repository.markSent(sentIds, LocalDateTime.now()));
        }
    }

    @Override
    public void afterPropertiesSet() {
        worker = Thread.ofPlatform().name(workerName).daemon().start(this::run);
    }

    @Override
    public void destroy() {
        worker.interrupt();
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                var batch = new ArrayList<T>(BATCH_SIZE);
                batch.add(queue.take());
                queue.drainTo(batch, BATCH_SIZE - 1);
                sendBatch(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                log.warn("{} 아웃박스 바로 전송이 예외로 끝났습니다. error={}", domain, e.getClass().getSimpleName());
            }
        }
    }

    private boolean send(T message) {
        try {
            sender.send(message);
            return true;
        } catch (OutboxSendException e) {
            message.recordFailure(e.getMessage());
            log.warn("{} 아웃박스 전송 실패로 이번 릴레이를 멈춥니다. attempt={} {}",
                domain, message.getAttemptCount(), e.getMessage());
            return false;
        }
    }
}
