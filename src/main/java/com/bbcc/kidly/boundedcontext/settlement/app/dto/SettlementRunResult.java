package com.bbcc.kidly.boundedcontext.settlement.app.dto;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRun;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 정산 실행 결과. 컨트롤러는 엔티티 대신 이것으로 응답을 만든다.
 *
 * @param triggerType SCHEDULED · MANUAL
 * @param status RUNNING · COMPLETED · PARTIAL_FAILED · FAILED
 * @param requestedBy 수동 실행한 관리자 ID. 자동 실행이면 null
 * @param finishedAt 끝나기 전에는 null
 */
public record SettlementRunResult(Long id, LocalDateTime cutoffAt, LocalDate periodStartOn, LocalDate periodEndOn,
    String triggerType, String status, int successCount, int failCount, Long requestedBy,
    LocalDateTime startedAt, LocalDateTime finishedAt) {

    public static SettlementRunResult from(SettlementRun run) {
        return new SettlementRunResult(run.getId(), run.getCutoffAt(), run.getPeriodStartOn(), run.getPeriodEndOn(),
            run.getTriggerType().name(), run.getStatus().name(), run.getSuccessCount(), run.getFailCount(),
            run.getRequestedBy(), run.getStartedAt(), run.getFinishedAt());
    }
}
