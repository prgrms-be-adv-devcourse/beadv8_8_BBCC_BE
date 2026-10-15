package com.bbcc.kidly.boundedcontext.settlement.app.port;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import java.time.LocalDate;

/**
 * 정산서 목록 조건. status · fromOn · toOn은 없으면 걸지 않는다.
 */
public record SettlementSearch(Long sellerId, SettlementStatus status, LocalDate fromOn, LocalDate toOn) {
}
