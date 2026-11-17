package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.dto.MemberDeadLetterMessage;
import com.bbcc.kidly.boundedcontext.member.app.dto.MemberDeadLetterResult;
import com.bbcc.kidly.boundedcontext.member.app.port.MemberDeadLetterReplay;
import com.bbcc.kidly.boundedcontext.member.domain.DeadLetterStatus;
import com.bbcc.kidly.boundedcontext.member.domain.MemberDeadLetterRepository;
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
public class MemberDeadLetterService {

    private final MemberDeadLetterRepository repository;
    private final MemberDeadLetterReplay replayer;
    private final TransactionTemplate transactionTemplate;

    @Transactional
    public boolean receive(MemberDeadLetterMessage message) {
        if (repository.existsByDltTopicAndDltPartitionAndDltOffset(
            message.dltTopic(), message.dltPartition(), message.dltOffset())) {
            return false;
        }
        repository.save(message.toDeadLetter());
        return true;
    }

    @Transactional(readOnly = true)
    public Page<MemberDeadLetterResult> list(String status, int page, int size) {
        var requested = DeadLetterStatus.valueOf(status);
        var pageable = PageRequest.of(page, size);
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            return new PageImpl<>(List.of(), pageable, repository.countByStatus(requested));
        }
        return repository.findByStatusOrderByIdDesc(requested, pageable).map(MemberDeadLetterResult::from);
    }

    @Transactional(readOnly = true)
    public MemberDeadLetterResult get(Long id) {
        return MemberDeadLetterResult.from(repository.getById(id));
    }

    public MemberDeadLetterResult reprocess(Long id, Long adminId) {
        repository.getById(id);
        try {
            var result = transactionTemplate.execute(status -> replay(id, adminId));
            log.info("회원 DLT 재처리: deadLetterId={} adminId={} status={}", id, adminId, result.status());
            return result;
        } catch (RuntimeException e) {
            var failureType = NestedExceptionUtils.getMostSpecificCause(e).getClass().getName();
            log.warn("회원 DLT 재처리 실패: deadLetterId={} adminId={} exception={}", id, adminId, failureType);
            return transactionTemplate.execute(status -> recordFailure(id, adminId, failureType));
        }
    }

    private MemberDeadLetterResult replay(Long id, Long adminId) {
        var deadLetter = repository.findByIdForUpdate(id).orElseThrow();
        if (deadLetter.isReprocessed()) {
            return MemberDeadLetterResult.from(deadLetter);
        }
        replayer.replay(MemberDeadLetterMessage.from(deadLetter));
        // 처리기가 영속성 컨텍스트를 비울 수 있어(@Modifying(clearAutomatically = true)) 다시 읽는다
        var replayed = repository.findByIdForUpdate(id).orElseThrow();
        replayed.markReprocessed(adminId, LocalDateTime.now());
        return MemberDeadLetterResult.from(replayed);
    }

    private MemberDeadLetterResult recordFailure(Long id, Long adminId, String failureType) {
        var deadLetter = repository.findByIdForUpdate(id).orElseThrow();
        if (!deadLetter.isReprocessed()) {
            deadLetter.recordReplayFailure(adminId, LocalDateTime.now(), failureType);
        }
        return MemberDeadLetterResult.from(deadLetter);
    }
}
