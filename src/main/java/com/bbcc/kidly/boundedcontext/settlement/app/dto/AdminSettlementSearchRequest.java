package com.bbcc.kidly.boundedcontext.settlement.app.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

/**
 * 관리자 정산서 목록 조건. 모두 선택이고, 판매자가 없으면 모든 판매자를 찾는다.
 */
public record AdminSettlementSearchRequest(
    @Positive(message = "sellerId는 0보다 커야 합니다")
    Long sellerId,
    @Pattern(regexp = "COMPLETED|PAID|FAILED", message = "status는 COMPLETED, PAID, FAILED 중 하나여야 합니다")
    String status,
    LocalDate fromOn,
    LocalDate toOn) {
}
