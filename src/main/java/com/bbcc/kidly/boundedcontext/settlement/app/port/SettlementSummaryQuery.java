package com.bbcc.kidly.boundedcontext.settlement.app.port;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 판매자의 정산서 목록 조회. 정렬은 정산 종료일 최신순으로 고정한다.
 */
public interface SettlementSummaryQuery {

    Page<SettlementSummaryRow> findAll(SettlementSearch search, Pageable pageable);
}
