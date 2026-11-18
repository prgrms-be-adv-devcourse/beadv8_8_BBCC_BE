package com.bbcc.kidly.member.support.deadletter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DLT 메시지 보관 · 조회 · 재처리. 도메인 모듈의 서비스가 상속해 자기 엔티티 · 리포지토리 · 재처리기를 넘긴다.
 */
public abstract class DeadLetterService<T extends DeadLetter> {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final String domain;
    private final DeadLetterRepository<T> repository;
    private final Function<DeadLetterMessage, T> factory;
    private final DeadLetterReplay replayer;
    private final TransactionTemplate transactionTemplate;

    protected DeadLetterService(String domain, DeadLetterRepository<T> repository,
        Function<DeadLetterMessage, T> factory, DeadLetterReplay replayer, TransactionTemplate transactionTemplate) {
        this.domain = domain;
        this.repository = repository;
        this.factory = factory;
        this.replayer = replayer;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional
    public boolean receive(DeadLetterMessage message) {
        if (repository.existsByDltTopicAndDltPartitionAndDltOffset(
            message.dltTopic(), message.dltPartition(), message.dltOffset())) {
            return false;
        }
        repository.save(factory.apply(message));
        return true;
    }

    @Transactional(readOnly = true)
    public Page<DeadLetterResult> list(String status, int page, int size) {
        var requested = DeadLetterStatus.valueOf(status);
        var pageable = PageRequest.of(page, size);
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            return new PageImpl<>(List.of(), pageable, repository.countByStatus(requested));
        }
        return repository.findByStatusOrderByIdDesc(requested, pageable).map(DeadLetterResult::from);
    }

    @Transactional(readOnly = true)
    public DeadLetterResult get(Long id) {
        return DeadLetterResult.from(repository.getById(id));
    }

    public DeadLetterResult reprocess(Long id, Long adminId) {
        repository.getById(id);
        try {
            var result = transactionTemplate.execute(status -> replay(id, adminId));
            log.info("{} DLT 재처리: deadLetterId={} adminId={} status={}", domain, id, adminId, result.status());
            return result;
        } catch (RuntimeException e) {
            var failureType = NestedExceptionUtils.getMostSpecificCause(e).getClass().getName();
            log.warn("{} DLT 재처리 실패: deadLetterId={} adminId={} exception={}", domain, id, adminId, failureType);
            return transactionTemplate.execute(status -> recordFailure(id, adminId, failureType));
        }
    }

    private DeadLetterResult replay(Long id, Long adminId) {
        var deadLetter = repository.findByIdForUpdate(id).orElseThrow();
        if (deadLetter.isReprocessed()) {
            return DeadLetterResult.from(deadLetter);
        }
        replayer.replay(DeadLetterMessage.from(deadLetter));
        // 처리기가 영속성 컨텍스트를 비울 수 있어(@Modifying(clearAutomatically = true)) 다시 읽는다
        var replayed = repository.findByIdForUpdate(id).orElseThrow();
        replayed.markReprocessed(adminId, LocalDateTime.now());
        return DeadLetterResult.from(replayed);
    }

    private DeadLetterResult recordFailure(Long id, Long adminId, String failureType) {
        var deadLetter = repository.findByIdForUpdate(id).orElseThrow();
        if (!deadLetter.isReprocessed()) {
            deadLetter.recordReplayFailure(adminId, LocalDateTime.now(), failureType);
        }
        return DeadLetterResult.from(deadLetter);
    }
}
