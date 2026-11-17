package com.bbcc.kidly.boundedcontext.childprofile.app;

import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileDeadLetterMessage;
import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileDeadLetterResult;
import com.bbcc.kidly.boundedcontext.childprofile.app.port.ChildProfileDeadLetterReplay;
import com.bbcc.kidly.boundedcontext.childprofile.domain.DeadLetterStatus;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileDeadLetterRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChildProfileDeadLetterService {

    private final ChildProfileDeadLetterRepository repository;
    private final ChildProfileDeadLetterReplay replayer;
    private final TransactionTemplate transactionTemplate;

    @Transactional
    public boolean receive(ChildProfileDeadLetterMessage message) {
        if (repository.existsByDltTopicAndDltPartitionAndDltOffset(
            message.dltTopic(), message.dltPartition(), message.dltOffset())) {
            return false;
        }
        repository.save(message.toDeadLetter());
        return true;
    }

    @Transactional(readOnly = true)
    public Page<ChildProfileDeadLetterResult> list(String status, int page, int size) {
        var requested = DeadLetterStatus.valueOf(status);
        var pageable = PageRequest.of(page, size);
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            return new PageImpl<>(List.of(), pageable, repository.countByStatus(requested));
        }
        return repository.findByStatusOrderByIdDesc(requested, pageable).map(ChildProfileDeadLetterResult::from);
    }

    @Transactional(readOnly = true)
    public ChildProfileDeadLetterResult get(Long id) {
        return ChildProfileDeadLetterResult.from(repository.getById(id));
    }

    public ChildProfileDeadLetterResult reprocess(Long id, Long adminId) {
        repository.getById(id);
        try {
            var result = transactionTemplate.execute(status -> replay(id, adminId));
            log.info("자녀 프로필 DLT 재처리: deadLetterId={} adminId={} status={}", id, adminId, result.status());
            return result;
        } catch (RuntimeException e) {
            var failureType = NestedExceptionUtils.getMostSpecificCause(e).getClass().getName();
            log.warn("자녀 프로필 DLT 재처리 실패: deadLetterId={} adminId={} exception={}", id, adminId, failureType);
            return transactionTemplate.execute(status -> recordFailure(id, adminId, failureType));
        }
    }

    private ChildProfileDeadLetterResult replay(Long id, Long adminId) {
        var deadLetter = repository.findByIdForUpdate(id).orElseThrow();
        if (deadLetter.isReprocessed()) {
            return ChildProfileDeadLetterResult.from(deadLetter);
        }
        replayer.replay(ChildProfileDeadLetterMessage.from(deadLetter));
        var replayed = repository.findByIdForUpdate(id).orElseThrow();
        replayed.markReprocessed(adminId, LocalDateTime.now());
        return ChildProfileDeadLetterResult.from(replayed);
    }

    private ChildProfileDeadLetterResult recordFailure(Long id, Long adminId, String failureType) {
        var deadLetter = repository.findByIdForUpdate(id).orElseThrow();
        if (!deadLetter.isReprocessed()) {
            deadLetter.recordReplayFailure(adminId, LocalDateTime.now(), failureType);
        }
        return ChildProfileDeadLetterResult.from(deadLetter);
    }
}
