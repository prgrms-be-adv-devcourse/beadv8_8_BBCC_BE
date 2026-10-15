package com.bbcc.kidly.boundedcontext.settlement.app.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * @param cutoffOn 종료 기준일. 이날 0시 전에 구매확정된 대상을 정산한다
 */
public record SettlementRunRequest(@NotNull LocalDate cutoffOn) {
}
