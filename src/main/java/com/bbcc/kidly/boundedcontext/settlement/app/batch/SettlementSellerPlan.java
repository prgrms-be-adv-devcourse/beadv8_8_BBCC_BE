package com.bbcc.kidly.boundedcontext.settlement.app.batch;

import java.util.List;

/**
 * 판매자 1명의 정산 계획. 실패한 묶음을 1명씩 다시 쓸 때 재사용되므로 엔티티 없이 값만 담는다.
 *
 * @param itemIds 정산 대상 ID 오름차순
 */
public record SettlementSellerPlan(Long sellerId, List<Long> itemIds, long totalSalesAmount) {
}
