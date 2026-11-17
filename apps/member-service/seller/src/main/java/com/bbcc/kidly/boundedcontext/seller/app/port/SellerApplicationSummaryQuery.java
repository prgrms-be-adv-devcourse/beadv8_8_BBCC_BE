package com.bbcc.kidly.boundedcontext.seller.app.port;

import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SellerApplicationSummaryQuery {

    Page<SellerApplicationSummary> findAll(SellerApplicationStatus status, Pageable pageable);
}
