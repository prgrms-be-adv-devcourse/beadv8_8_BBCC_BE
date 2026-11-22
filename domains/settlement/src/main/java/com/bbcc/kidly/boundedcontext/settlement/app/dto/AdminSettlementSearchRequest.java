package com.bbcc.kidly.boundedcontext.settlement.app.dto;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSearch;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record AdminSettlementSearchRequest(
    @Positive(message = "sellerId는 0보다 커야 합니다")
    Long sellerId,
    @Pattern(regexp = "COMPLETED|PAID|FAILED", message = "status는 COMPLETED, PAID, FAILED 중 하나여야 합니다")
    String status,
    LocalDate fromOn,
    LocalDate toOn) {

    public SettlementSearch toSearch() {
        return new SettlementSearch(sellerId, status == null ? null : SettlementStatus.valueOf(status), fromOn, toOn);
    }
}
