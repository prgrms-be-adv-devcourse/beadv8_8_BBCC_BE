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

    // DLT는 파티션 1개를 컨테이너 하나가 차례로 읽어 같은 위치를 동시에 저장하지 않는다. 겹치면 유니크 제약이 막는다
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
        // 없는 id는 실패 이력을 남기지 않고 404로 끝낸다
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

    // 행 잠금으로 동시 재처리를 한 줄로 세운다. 처리기는 이 트랜잭션에 참여해 실패하면 함께 롤백된다
    private ChildProfileDeadLetterResult replay(Long id, Long adminId) {
        var deadLetter = repository.findByIdForUpdate(id).orElseThrow();
        if (deadLetter.isReprocessed()) {
            return ChildProfileDeadLetterResult.from(deadLetter);
        }
        replayer.replay(ChildProfileDeadLetterMessage.from(deadLetter));
        // 처리기가 영속성 컨텍스트를 비울 수 있어(@Modifying(clearAutomatically = true)) 다시 읽는다
        var replayed = repository.findByIdForUpdate(id).orElseThrow();
        replayed.markReprocessed(adminId, LocalDateTime.now());
        return ChildProfileDeadLetterResult.from(replayed);
    }

    private ChildProfileDeadLetterResult recordFailure(Long id, Long adminId, String failureType) {
        var deadLetter = repository.findByIdForUpdate(id).orElseThrow();
        // 실패한 뒤 잠금을 기다리던 다른 재처리가 성공했을 수 있다
        if (!deadLetter.isReprocessed()) {
            deadLetter.recordReplayFailure(adminId, LocalDateTime.now(), failureType);
        }
        return ChildProfileDeadLetterResult.from(deadLetter);
    }
}
