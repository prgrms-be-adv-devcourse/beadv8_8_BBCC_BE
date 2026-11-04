package com.bbcc.kidly.boundedcontext.settlement.in.dto;

import com.bbcc.kidly.boundedcontext.settlement.app.dto.SettlementTargetResult;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @param expectedPayoutOn 정산 예정일(확정일 다음 영업일). 공휴일을 조회하지 못해 알 수 없으면 null
 */
public record SettlementTargetResponse(Long orderItemId, Long orderId, long salesAmount, LocalDateTime confirmedAt,
                                       LocalDate expectedPayoutOn) {

    public static SettlementTargetResponse from(SettlementTargetResult result) {
        return new SettlementTargetResponse(result.orderItemId(), result.orderId(), result.salesAmount(),
            result.confirmedAt(), result.expectedPayoutOn());
    }
}
