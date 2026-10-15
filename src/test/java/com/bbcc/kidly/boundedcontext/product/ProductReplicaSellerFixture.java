package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSeller;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.time.LocalDateTime;

final class ProductReplicaSellerFixture {

    static final long SELLER_ID = 2L;

    static final String BUSINESS_NAME = "테스트상점";

    static final LocalDateTime SELLER_CHANGED_AT = LocalDateTime.of(2026, 10, 15, 10, 0);

    private ProductReplicaSellerFixture() {
    }

    /**
     * 판매자 2가 승인되었을 때의 판매자 정보 변경이다.
     */
    static ProductReplicaSeller.ChangeInfo sellerApproved() {
        return sellerChangeAt(BUSINESS_NAME, SellerStatus.ACTIVE, SELLER_CHANGED_AT);
    }

    /**
     * 판매자 2의 상호명 · 상태 · 발생 시각만 바꾼 판매자 정보 변경이다. 어느 변경이 반영됐는지 상호명과 상태로 구분한다.
     */
    static ProductReplicaSeller.ChangeInfo sellerChangeAt(String businessName, SellerStatus status,
        LocalDateTime occurredAt) {
        return new ProductReplicaSeller.ChangeInfo(SELLER_ID, businessName, status, occurredAt);
    }
}
