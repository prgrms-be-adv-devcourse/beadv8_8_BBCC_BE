package com.bbcc.kidly.boundedcontext.settlement.app.port;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 대기 상태이고 종료 기준일 전에 확정된 대상이 있는 판매자를 ID 오름차순으로 돌려준다.
 */
public interface SettlementSellerQuery {

    List<Long> findSellerIdsToSettle(LocalDateTime cutoffAt, Long afterSellerId, int limit);
}
