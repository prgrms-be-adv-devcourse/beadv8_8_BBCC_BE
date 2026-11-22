package com.bbcc.kidly.boundedcontext.settlement.app.dto;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSearch;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record SettlementSearchRequest(
    @Pattern(regexp = "COMPLETED|PAID|FAILED", message = "status는 COMPLETED, PAID, FAILED 중 하나여야 합니다")
    String status,
    LocalDate fromOn,
    LocalDate toOn) {

    public SettlementSearch toSearch(Long sellerId) {
        return new SettlementSearch(sellerId, status == null ? null : SettlementStatus.valueOf(status), fromOn, toOn);
    }
}
