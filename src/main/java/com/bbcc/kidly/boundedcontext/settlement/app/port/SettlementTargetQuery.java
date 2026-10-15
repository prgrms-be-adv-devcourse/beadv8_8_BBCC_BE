package com.bbcc.kidly.boundedcontext.settlement.app.port;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 판매자의 정산 대상(아직 정산서에 들어가지 않은 항목) 조회. 정렬은 확정 시각 최신순으로 고정한다.
 */
public interface SettlementTargetQuery {

    Page<SettlementTargetRow> findPending(Long sellerId, Pageable pageable);
}
