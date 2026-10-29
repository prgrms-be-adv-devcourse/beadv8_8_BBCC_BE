package com.bbcc.kidly.boundedcontext.search.out;

import com.bbcc.kidly.boundedcontext.search.domain.SearchSeller;
import java.time.format.DateTimeFormatter;

/**
 * Elasticsearch 판매자 색인에 저장하는 문서다. 상품 목록에 상호명을 붙일 때 판매자 ID로 찾는다.
 */
public record SellerDocument(Long sellerId, String businessName, String occurredAt) {

    static SellerDocument from(SearchSeller seller) {
        return new SellerDocument(seller.getSellerId(), seller.getBusinessName(),
            seller.getOccurredAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
}
