package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetterReplay;
import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters;
import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters.Received;
import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters.State;
import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters.Summary;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewTimes;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재처리는 원래 처리기의 트랜잭션으로 끝내고 결과만 별도 기록한다. DLT 잠금을 쥔 채 새 DB 연결을 기다리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityDeadLetterService {

    private final CommunityDeadLetters repository;
    private final CommunityDeadLetterReplay replayer;
    private final ReviewSeparateTransaction separateTransaction;

    @Transactional
    public boolean receive(Received received) {
        return repository.insert(received, ReviewTimes.now());
    }

    @Transactional(readOnly = true)
    public Page<Summary> list(String state, int page, int size) {
        State requested;
        try {
            requested = state == null ? State.OPEN : State.valueOf(state);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ReviewErrorCode.INVALID_DEAD_LETTER_STATE);
        }
        PageRequest pageable = PageRequest.of(page, size);
        return pageable.getOffset() > Integer.MAX_VALUE
            ? new PageImpl<>(List.of(), pageable, repository.countByState(requested))
            : repository.findByState(requested, pageable);
    }

    @Transactional(readOnly = true)
    public Summary get(Long id) {
        return repository.findSummary(id)
            .orElseThrow(() -> new BusinessException(ReviewErrorCode.DEAD_LETTER_NOT_FOUND));
    }

    public Summary reprocess(Long id, Long adminId) {
        var target = repository.findForReplay(id)
            .orElseThrow(() -> new BusinessException(ReviewErrorCode.DEAD_LETTER_NOT_FOUND));
        if (target.state() == State.REPROCESSED) {
            return get(id);
        }
        try {
            replayer.replay(target.message());
        } catch (RuntimeException e) {
            String failureType = NestedExceptionUtils.getMostSpecificCause(e).getClass().getName();
            separateTransaction.run(() -> repository.recordReplayFailure(id, adminId, ReviewTimes.now(), failureType));
            log.warn("커뮤니티 DLT 재처리 실패: deadLetterId={} adminId={} exception={}", id, adminId, failureType);
            return get(id);
        }
        separateTransaction.run(() -> repository.markReprocessed(id, adminId, ReviewTimes.now()));
        log.info("커뮤니티 DLT 재처리 완료: deadLetterId={} adminId={}", id, adminId);
        return get(id);
    }
}
