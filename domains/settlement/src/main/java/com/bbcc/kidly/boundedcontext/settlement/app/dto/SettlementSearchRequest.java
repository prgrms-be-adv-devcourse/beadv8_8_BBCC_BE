package com.bbcc.kidly.boundedcontext.settlement.app.dto;

import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * 모두 선택이다.
 */
public record SettlementSearchRequest(
    @Pattern(regexp = "COMPLETED|PAID|FAILED", message = "status는 COMPLETED, PAID, FAILED 중 하나여야 합니다")
    String status,
    LocalDate fromOn,
    LocalDate toOn) {
}
