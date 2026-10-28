package com.bbcc.kidly.boundedcontext.settlement.app.port;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 정산할 판매자 ID 목록. 대기 상태이고 종료 기준일 전에 확정된 대상이 있는 판매자를 ID 오름차순으로 돌려준다.
 */
public interface SettlementSellerQuery {

    // afterSellerId보다 큰 ID부터 limit개
    List<Long> findSellerIdsToSettle(LocalDateTime cutoffAt, Long afterSellerId, int limit);
}
