package com.bbcc.kidly.boundedcontext.settlement.app.port;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 정산서 목록 조회. 정렬은 정산 종료일 최신순으로 고정하고,
 * 기간(fromOn · toOn)은 정산 종료일 기준이다.
 */
public interface SettlementSummaryQuery {

    Page<SettlementSummaryRow> findAll(SettlementSearch search, Pageable pageable);
}
