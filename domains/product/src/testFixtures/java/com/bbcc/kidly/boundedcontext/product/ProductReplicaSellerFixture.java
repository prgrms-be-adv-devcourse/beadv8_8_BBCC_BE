package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSeller;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.time.LocalDateTime;
import java.util.UUID;

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

    /**
     * 판매자 도메인이 판매자 2를 승인할 때 보내는 판매자 정보 변경됨이다. 값은 {@link #sellerApproved()}와 같다.
     */
    static SellerChangedEvent sellerApprovedEvent() {
        return sellerChangedEventAt(BUSINESS_NAME, SellerStatus.ACTIVE, SELLER_CHANGED_AT);
    }

    /**
     * 판매자 2의 상호명 · 상태 · 발생 시각만 바꾼 판매자 정보 변경됨이다.
     */
    static SellerChangedEvent sellerChangedEventAt(String businessName, SellerStatus status,
        LocalDateTime occurredAt) {
        return new SellerChangedEvent(UUID.randomUUID(), occurredAt,
            new SellerChangedEvent.Data(SELLER_ID, businessName, status));
    }
}
