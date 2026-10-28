package com.bbcc.kidly.boundedcontext.seller.app.port;

import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 관리자 판매자 신청 목록 조회.
 */
public interface SellerApplicationSummaryQuery {

    // status가 null이면 전체
    Page<SellerApplicationSummary> findAll(SellerApplicationStatus status, Pageable pageable);
}
