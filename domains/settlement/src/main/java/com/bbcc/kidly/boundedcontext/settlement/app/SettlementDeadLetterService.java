package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementDeadLetterReplay;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementDeadLetters;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementDeadLetters.Received;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementDeadLetters.State;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementDeadLetters.Summary;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementErrorCode;
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
 * 정산 DLT 보관 · 조회 · 재처리. 재처리는 원래 처리기의 트랜잭션으로 끝내고 결과만 별도로 기록한다.
 * DLT 잠금을 쥔 채 새 DB 연결을 기다리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementDeadLetterService {

    private final SettlementDeadLetters repository;
    private final SettlementDeadLetterReplay replayer;

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
            throw new BusinessException(SettlementErrorCode.INVALID_DEAD_LETTER_STATE);
        }
        PageRequest pageable = PageRequest.of(page, size);
        return pageable.getOffset() > Integer.MAX_VALUE
            ? new PageImpl<>(List.of(), pageable, repository.countByState(requested))
            : repository.findByState(requested, pageable);
    }

    @Transactional(readOnly = true)
    public Summary get(Long id) {
        return repository.findSummary(id)
            .orElseThrow(() -> new BusinessException(SettlementErrorCode.DEAD_LETTER_NOT_FOUND));
    }

    // 트랜잭션을 걸지 않는다: 반영은 원래 처리기의 트랜잭션, 결과 기록은 저장소의 짧은 조건부 UPDATE다
    public Summary reprocess(Long id, Long adminId) {
        var target = repository.findForReplay(id)
            .orElseThrow(() -> new BusinessException(SettlementErrorCode.DEAD_LETTER_NOT_FOUND));
        if (target.state() == State.REPROCESSED) {
            return get(id);
        }
        // 두 관리자가 동시에 재처리하면 처리기가 두 번 돌 수 있다. 처리기는 멱등하다: 구매확정은 주문상품 ID 유일 키로 항목 1건,
        // 지급 결과는 이미 PAID면 그대로 둔다(SettlementKafkaReceiveIntegrationTest). 기록은 markReprocessed의 조건부 UPDATE로 한 번만 남는다
        try {
            replayer.replay(target.message());
        } catch (RuntimeException e) {
            String failureType = NestedExceptionUtils.getMostSpecificCause(e).getClass().getName();
            repository.recordReplayFailure(id, adminId, LocalDateTime.now(), failureType);
            log.warn("정산 DLT 재처리 실패: deadLetterId={} adminId={} exception={}", id, adminId, failureType);
            return get(id);
        }
        repository.markReprocessed(id, adminId, LocalDateTime.now());
        log.info("정산 DLT 재처리 완료: deadLetterId={} adminId={}", id, adminId);
        return get(id);
    }
}
