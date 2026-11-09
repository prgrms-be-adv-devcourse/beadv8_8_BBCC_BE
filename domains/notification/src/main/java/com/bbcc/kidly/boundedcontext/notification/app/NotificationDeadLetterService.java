package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetterReplay;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetters;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetters.Received;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetters.State;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetters.Summary;
import com.bbcc.kidly.global.error.BusinessException;
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

/**
 * 알림 DLT 보관 · 조회 · 재처리. 재처리는 원래 처리기의 트랜잭션으로 끝내고 결과만 별도로 기록한다.
 * DLT 잠금을 쥔 채 새 DB 연결을 기다리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDeadLetterService {

    private final NotificationDeadLetters repository;
    private final NotificationDeadLetterReplay replayer;

    @Transactional
    public boolean receive(Received received) {
        return repository.insert(received, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public Page<Summary> list(String state, int page, int size) {
        State requested;
        try {
            requested = state == null ? State.OPEN : State.valueOf(state);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(NotificationErrorCode.INVALID_DEAD_LETTER_STATE);
        }
        PageRequest pageable = PageRequest.of(page, size);
        return pageable.getOffset() > Integer.MAX_VALUE
            ? new PageImpl<>(List.of(), pageable, repository.countByState(requested))
            : repository.findByState(requested, pageable);
    }

    @Transactional(readOnly = true)
    public Summary get(Long id) {
        return repository.findSummary(id)
            .orElseThrow(() -> new BusinessException(NotificationErrorCode.DEAD_LETTER_NOT_FOUND));
    }

    // 트랜잭션을 걸지 않는다: 반영은 원래 처리기의 트랜잭션, 결과 기록은 저장소의 짧은 조건부 UPDATE다
    public Summary reprocess(Long id, Long adminId) {
        var target = repository.findForReplay(id)
            .orElseThrow(() -> new BusinessException(NotificationErrorCode.DEAD_LETTER_NOT_FOUND));
        if (target.state() == State.REPROCESSED) {
            return get(id);
        }
        // 두 관리자가 동시에 재처리하면 처리기가 두 번 돌 수 있다. 처리기는 멱등하다: 메일 · 푸시는 이벤트 키 · 채널로, 알림함은 중복 키로 한 건이다.
        // 재처리는 원래 순서를 뒤집을 수 있다. 판매자 · 상품 복사본은 더 새로운 변경만 반영하고(changeIfNewer · applyIfNewer),
        // 자녀 계정은 삭제 한 방향이며, 탈퇴한 회원의 가입은 탈퇴 표시로 건너뛴다(MemberWithdrawalService).
        // 기록은 markReprocessed의 조건부 UPDATE로 한 번만 남는다
        try {
            replayer.replay(target.message());
        } catch (RuntimeException e) {
            String failureType = NestedExceptionUtils.getMostSpecificCause(e).getClass().getName();
            repository.recordReplayFailure(id, adminId, LocalDateTime.now(), failureType);
            log.warn("알림 DLT 재처리 실패: deadLetterId={} adminId={} exception={}", id, adminId, failureType);
            return get(id);
        }
        repository.markReprocessed(id, adminId, LocalDateTime.now());
        log.info("알림 DLT 재처리 완료: deadLetterId={} adminId={}", id, adminId);
        return get(id);
    }
}
