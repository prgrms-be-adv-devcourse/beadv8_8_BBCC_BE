package com.bbcc.kidly.boundedcontext.settlement.domain;

import java.time.LocalDateTime;

/** 구매확정된 주문항목으로 정산항목을 생성하기 위한 입력값. 검증은 엔티티가 담당한다. */
public record SettlementItemCreateInfo(
        Long orderId,
        Long orderItemId,
        Long sellerId,
        Long salesAmount,
        LocalDateTime confirmedAt) {
}
