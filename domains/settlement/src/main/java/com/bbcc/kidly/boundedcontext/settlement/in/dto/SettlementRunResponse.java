package com.bbcc.kidly.boundedcontext.settlement.in.dto;

import com.bbcc.kidly.boundedcontext.settlement.app.dto.SettlementRunResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @param triggerType SCHEDULED · MANUAL
 * @param status RUNNING · COMPLETED · PARTIAL_FAILED · FAILED
 * @param requestedBy 수동 실행한 관리자 ID. 자동 실행이면 null
 * @param finishedAt 끝나기 전에는 null
 * @param skippedSellers 건너뛴 판매자. 없으면 []
 */
public record SettlementRunResponse(Long runId, LocalDateTime cutoffAt, LocalDate periodStartOn, LocalDate periodEndOn,
    String triggerType, String status, int successCount, int failCount, Long requestedBy,
    LocalDateTime startedAt, LocalDateTime finishedAt, List<SkippedSeller> skippedSellers) {

    public static SettlementRunResponse from(SettlementRunResult result) {
        return new SettlementRunResponse(result.runId(), result.cutoffAt(), result.periodStartOn(),
            result.periodEndOn(), result.triggerType(), result.status(), result.successCount(), result.failCount(),
            result.requestedBy(), result.startedAt(), result.finishedAt(),
            result.skippedSellers().stream().map(SkippedSeller::from).toList());
    }

    /**
     * @param phase PROCESS · WRITE
     * @param reason 예외 이름: 메시지 (200자까지)
     */
    public record SkippedSeller(Long sellerId, String phase, String reason) {

        static SkippedSeller from(SettlementRunResult.SkippedSeller seller) {
            return new SkippedSeller(seller.sellerId(), seller.phase(), seller.reason());
        }
    }
}
