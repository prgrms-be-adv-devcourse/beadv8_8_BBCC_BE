package com.bbcc.kidly.boundedcontext.settlement.app.dto;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementTargetRow;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 판매자의 정산 대상 한 건. 예정일은 확정일 다음 영업일이고, 공휴일을 조회하지 못해 알 수 없으면 null이다.
 */
public record SettlementTargetResult(Long orderItemId, Long orderId, long salesAmount, LocalDateTime confirmedAt,
                                     LocalDate expectedPayoutOn) {

    public static SettlementTargetResult of(SettlementTargetRow row, LocalDate expectedPayoutOn) {
        return new SettlementTargetResult(row.orderItemId(), row.orderId(), row.salesAmount(), row.confirmedAt(),
            expectedPayoutOn);
    }
}
