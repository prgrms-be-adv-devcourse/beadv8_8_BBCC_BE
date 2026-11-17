package com.bbcc.kidly.boundedcontext.product.app.port.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SellerProductQuery {

    Page<SellerProductSummary> findBySeller(Long sellerId, SellerProductFilter filter, Pageable pageable);
}
